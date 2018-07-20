package com.michelin.cio.hudson.plugins.copytoslave;

import com.sun.jna.Native;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.org.apache.tools.tar.TarInputStream;
import hudson.remoting.Future;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.tar.TarEntry;

import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static hudson.Util.fixEmpty;
import static hudson.util.jna.GNUCLibrary.LIBC;

public class FilePathUtils implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(FilePathUtils.class.getName());

    /**
     * Same as FilePath.copyRecursiveTo but not includes default excludes
     * Copies the files that match the given file mask to the specified target node.
     *
     * @param fileMask Ant GLOB pattern.
     *                 String like "foo/bar/*.xml" Multiple patterns can be separated
     *                 by ',', and whitespace can surround ',' (so that you can write
     *                 "abc, def" and "abc,def" to mean the same thing.
     * @param excludes Files to be excluded. Can be null.
     * @param source source FilePath
     * @param target target FilePath
     * @return the number of files copied.
     */
    public static int copyRecursiveTo(final String fileMask, final String excludes, final FilePath source, final FilePath target) throws IOException, InterruptedException {
        if(source.getChannel() == target.getChannel()) {
            // local to local copy.
            return source.act(new FilePath.FileCallable<Integer>() {
                private static final long serialVersionUID = 1L;

                public Integer invoke(File base, VirtualChannel channel) throws IOException {
                    if(!base.exists()) return 0;
                    assert target.getChannel() == null;

                    try {
                        class CopyImpl extends Copy {
                            private int copySize;

                            public CopyImpl() {
                                setProject(new org.apache.tools.ant.Project());
                            }

                            @Override
                            protected void doFileOperations() {
                                copySize = super.fileCopyMap.size();
                                super.doFileOperations();
                            }

                            public int getNumCopied() {
                                return copySize;
                            }
                        }

                        CopyImpl copyTask = new CopyImpl();
                        copyTask.setTodir(new File(target.getRemote()));
                        copyTask.addFileset(Util.createFileSet(base, fileMask, excludes));
                        copyTask.setOverwrite(true);
                        copyTask.setIncludeEmptyDirs(false);

                        copyTask.execute();
                        return copyTask.getNumCopied();
                    } catch(BuildException e) {
                        throw new IOException2("Failed to copy " + base + "/" + fileMask + " to " + target, e);
                    }
                }
            });
        } else if(source.getChannel() == null) {
            // local -> remote copy
            final Pipe pipe = Pipe.createLocalToRemote();

            Future<Void> future = target.actAsync(new FilePath.FileCallable<Void>() {
                private static final long serialVersionUID = 1L;

                public Void invoke(File f, VirtualChannel channel) throws IOException {
                    try {
                        readFromTar(source.getRemote() + '/' + fileMask, f, FilePath.TarCompression.GZIP.extract(pipe.getIn()));
                        return null;
                    } finally {
                        pipe.getIn().close();
                    }
                }
            });
            int r = writeToTar(new File(source.getRemote()), fileMask, excludes, FilePath.TarCompression.GZIP.compress(pipe.getOut()));
            try {
                future.get();
            } catch(ExecutionException e) {
                throw new IOException2(e);
            }
            return r;
        } else {
            // remote -> local copy
            final Pipe pipe = Pipe.createRemoteToLocal();

            Future<Integer> future = source.actAsync(new FilePath.FileCallable<Integer>() {
                private static final long serialVersionUID = 1L;

                public Integer invoke(File f, VirtualChannel channel) throws IOException {
                    try {
                        return writeToTar(f, fileMask, excludes, FilePath.TarCompression.GZIP.compress(pipe.getOut()));
                    } finally {
                        pipe.getOut().close();
                    }
                }
            });
            try {
                readFromTar(source.getRemote() + '/' + fileMask, new File(target.getRemote()), FilePath.TarCompression.GZIP.extract(pipe.getIn()));
            } catch(IOException e) {// BuildException or IOException
                try {
                    future.get(3, TimeUnit.SECONDS);
                    throw e;    // the remote side completed successfully, so the error must be local
                } catch(ExecutionException x) {
                    // report both errors
                    throw new IOException2(Functions.printThrowable(e), x);
                } catch(TimeoutException _) {
                    // remote is hanging
                    throw e;
                }
            }
            try {
                return future.get();
            } catch(ExecutionException e) {
                throw new IOException2(e);
            }
        }
    }

    /**
     * Writes to a tar stream and stores obtained files to the base dir.
     *
     * @return number of files/directories that are written.
     */
    private static Integer writeToTar(File baseDir, String fileMask, String excludes, OutputStream out) throws IOException {
        Archiver tw = ArchiverFactory.TAR.create(out);
        try {
            new EmptyGlob(fileMask, excludes).scan(baseDir, tw);
        } finally {
            tw.close();
        }
        return tw.countEntries();
    }

    private static void readFromTar(String name, File baseDir, InputStream in) throws IOException {
        TarInputStream t = new TarInputStream(in);
        try {
            TarEntry te;
            while((te = t.getNextEntry()) != null) {
                File f = new File(baseDir, te.getName());
                if(te.isDirectory()) {
                    f.mkdirs();
                } else {
                    File parent = f.getParentFile();
                    if(parent != null) parent.mkdirs();

                    byte linkFlag = (Byte)LINKFLAG_FIELD.get(te);
                    if(linkFlag == TarEntry.LF_SYMLINK) {
                        new FilePath(f).symlinkTo(te.getLinkName(), TaskListener.NULL);
                    } else {
                        IOUtils.copy(t, f);

                        f.setLastModified(te.getModTime().getTime());
                        int mode = te.getMode() & 0777;
                        if(mode != 0 && !Functions.isWindows()) // be defensive
                            _chmod(f, mode);
                    }
                }
            }
        } catch(IOException e) {
            throw new IOException2("Failed to extract " + name, e);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); // process this later
            throw new IOException2("Failed to extract " + name, e);
        } catch(IllegalAccessException e) {
            throw new IOException2("Failed to extract " + name, e);
        } finally {
            t.close();
        }
    }

    private static final Field LINKFLAG_FIELD = getTarEntryLinkFlagField();

    private static Field getTarEntryLinkFlagField() {
        try {
            Field f = TarEntry.class.getDeclaredField("linkFlag");
            f.setAccessible(true);
            return f;
        } catch(SecurityException e) {
            throw new AssertionError(e);
        } catch(NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean CHMOD_WARNED = false;

    private static void _chmodAnt(File f, int mask) {
        if(!CHMOD_WARNED) { // only warn this once to avoid flooding the log
            CHMOD_WARNED = true;
            LOGGER.warning("GNU C Library not available: Using Ant's chmod task instead.");
        }
        Chmod chmodTask = new Chmod();
        chmodTask.setProject(new Project());
        chmodTask.setFile(f);
        chmodTask.setPerm(Integer.toOctalString(mask));
        chmodTask.execute();
    }

    /**
     * Run chmod via libc if we can, otherwise fall back to Ant.
     */
    private static void _chmod(File f, int mask) throws IOException {
        if(Functions.isWindows()) return; // noop

        try {
            if(LIBC.chmod(f.getAbsolutePath(), mask) != 0) {
                throw new IOException("Failed to chmod " + f + " : " + LIBC.strerror(Native.getLastError()));
            }
        } catch(NoClassDefFoundError e) {  // cf. https://groups.google.com/group/hudson-dev/browse_thread/thread/6d16c3e8ea0dbc9?hl=fr
            _chmodAnt(f, mask);
        } catch(UnsatisfiedLinkError e2) { // HUDSON-8155: use Ant's chmod task on non-GNU C systems
            _chmodAnt(f, mask);
        }
    }

    private static class EmptyGlob extends DirScanner {
        private final String includes, excludes;

        private boolean useDefaultExcludes = false;

        public EmptyGlob(String includes, String excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }

        public EmptyGlob(String includes, String excludes, boolean useDefaultExcludes) {
            this(includes, excludes);
            this.useDefaultExcludes = useDefaultExcludes;
        }

        public void scan(File dir, FileVisitor visitor) throws IOException {
            if(fixEmpty(includes) == null && excludes == null) {
                // optimization
                new Full().scan(dir, visitor);
                return;
            }

            FileSet fs = Util.createFileSet(dir, includes, excludes);
            fs.setDefaultexcludes(useDefaultExcludes);

            if(dir.exists()) {
                DirectoryScanner ds = fs.getDirectoryScanner(new org.apache.tools.ant.Project());
                for(String f : ds.getIncludedFiles()) {
                    File file = new File(dir, f);

                    if(visitor.understandsSymlink()) {
                        try {
                            String target;
                            try {
                                target = Util.resolveSymlink(file);
                            } catch(IOException x) { // JENKINS-13202
                                target = null;
                            }
                            if(target != null) {
                                visitor.visitSymlink(file, target, f);
                                continue;
                            }
                        } catch(InterruptedException e) {
                            throw (IOException)new InterruptedIOException().initCause(e);
                        }
                    }
                    visitor.visit(file, f);
                }
            }
        }

        private static final long serialVersionUID = 1L;
    }
}
