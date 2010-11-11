/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Eric Lefevre-Ardant, Erik Ramfelt, Michael B. Donohue, Alan Harder,
 * Manufacture Française des Pneumatiques Michelin, Romain Seguy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.michelin.cio.hudson.plugins.copytoslave;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.FilePath.TarCompression;
import hudson.Functions;
import hudson.org.apache.tools.tar.TarInputStream;
import hudson.remoting.Future;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;
import static hudson.util.jna.GNUCLibrary.LIBC;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import org.apache.tools.tar.TarEntry;

/**
 * Complements Hudson's {@link FilePath} to enhance the {@code copyRecursiveTo()}
 * method so that it doesn't use Ant's default excludes (cf. HUDSON-7999).
 */
public class MyFilePath {

    /**
     * Enhances Hudson's {@link FilePath#copyRecursiveTo} until I patch Hudson
     * core and upgrade the plugin to the corresponding Hudson version.
     *
     * <p>This method supports only local to remote copy.</p>
     */
    public static int copyRecursiveTo(final FilePath source, final String fileMask, final String excludes, final FilePath target) throws IOException, InterruptedException {
        // local -> remote copy
        final Pipe pipe = Pipe.createLocalToRemote();

        Future<Void> future = target.actAsync(new FileCallable<Void>() {
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                try {
                    readFromTar(source.getRemote() + '/' + fileMask, f, TarCompression.GZIP.extract(pipe.getIn()));
                    return null;
                } finally {
                    pipe.getIn().close();
                }
            }
        });
        int r = writeToTar(new File(source.getRemote()), fileMask, excludes, TarCompression.GZIP.compress(pipe.getOut()));
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new IOException2(e);
        }
        return r;
    }

    /**
     * Full copy/paste of Hudson's {@link FilePath#readFromTar} method with
     * minor tweaking.
     *
     * @see hudson.FilePath#readFromTar(java.lang.String, java.io.File, java.io.InputStream) 
     */
    public static void readFromTar(String name, File baseDir, InputStream in) throws IOException {
        TarInputStream t = new TarInputStream(in);
        try {
            TarEntry te;
            while ((te = t.getNextEntry()) != null) {
                File f = new File(baseDir, te.getName());
                if(te.isDirectory()) {
                    f.mkdirs();
                } else {
                    File parent = f.getParentFile();
                    if (parent != null) parent.mkdirs();

                    IOUtils.copy(t,f);
                    f.setLastModified(te.getModTime().getTime());
                    int mode = te.getMode()&0777;
                    if(mode!=0 && !Functions.isWindows()) // be defensive
                        try {
                            LIBC.chmod(f.getPath(), mode);
                        } catch (NoClassDefFoundError e) {
                            // be defensive. see http://www.nabble.com/-3.0.6--Site-copy-problem%3A-hudson.util.IOException2%3A--java.lang.NoClassDefFoundError%3A-Could-not-initialize-class--hudson.util.jna.GNUCLibrary-td23588879.html
                        }
                }
            }
        } catch(IOException e) {
            throw new IOException2("Failed to extract "+name,e);
        } finally {
            t.close();
        }
    }

    /**
     * Full copy/paste of Hudson's {@link FilePath#writeToTar} method with minor
     * tweaking.
     *
     * @see hudson.FilePath#writeToTar(java.lang.String, java.io.File, java.io.InputStream)
     */
    public static Integer writeToTar(File baseDir, String fileMask, String excludes, OutputStream out) throws IOException {
        Archiver tw = ArchiverFactory.TAR.create(out);
        try {
            new MyGlobDirScanner(fileMask, excludes).scan(baseDir,tw);  // HUDSON-7999
        } finally {
            tw.close();
        }
        return tw.countEntries();
    }

}
