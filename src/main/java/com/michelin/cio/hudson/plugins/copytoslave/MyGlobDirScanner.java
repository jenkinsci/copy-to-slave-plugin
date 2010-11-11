/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc., Manufacture Française des Pneumatiques Michelin,
 * Romain Seguy
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

import hudson.Util;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.IOException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

/**
 * Modified copy of  Hudson's {@link DirScanner.Glob} to modify the {@code scan()}
 * method so that it takes into account Ant's default excludes (cf. HUDSON-7999).
 */
public class MyGlobDirScanner extends DirScanner {

    private final String includes;
    private final String excludes;

    MyGlobDirScanner(String includes, String excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    public void scan(File dir, FileVisitor visitor) throws IOException {
        if(Util.fixEmpty(includes)==null && excludes==null) {
            // optimization
            new Full().scan(dir,visitor);
            return;
        }

        FileSet fs = Util.createFileSet(dir,includes,excludes);
        fs.setDefaultexcludes(false); // HUDSON-7999

        if(dir.exists()) {
            DirectoryScanner ds = fs.getDirectoryScanner(new org.apache.tools.ant.Project());
            for( String f : ds.getIncludedFiles()) {
                File file = new File(dir, f);
                visitor.visit(file,f);
            }
        }
    }

}
