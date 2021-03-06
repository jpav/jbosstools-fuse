/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.fusesource.ide.commons.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.fusesource.ide.commons.camel.tools.Strings;
import org.fusesource.ide.commons.Activator;

/**
 * @author lhein
 */
public class IOUtils {
	
	public static String includeRegEx = "\"@include(\"(.+)\")\".r";
	
    /**
     * reads text from an input stream with the given encoding
     * 
     * @param stream
     * @param encoding
     * @return
     * @throws IOException
     */
    public static String loadText(InputStream stream, String encoding) throws IOException {
    	if (stream == null) return "";
    	if (Strings.isEmpty(encoding)) encoding = "UTF-8";
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	try {
    		copy(stream, baos);
    	} finally {
    		stream.close();
    	}
    	return new String(baos.toByteArray(), encoding);
    }
    
    public static String loadTextFile(File file, String encoding) {
    	if (file == null) return "";
    	if (Strings.isEmpty(encoding)) encoding = "UTF-8";
    	
    	try {
    		return new String(loadBinaryFile(file), encoding);
    	} catch (UnsupportedEncodingException e) {
    		Activator.getLogger().error(e);
    	}
    	return "";
    }

    public static byte[] loadBinaryFile(File file) {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream in = null;
        try {
        	in = new FileInputStream(file);
        	copy(in, baos);
        } catch (IOException ex) {
        	Activator.getLogger().error(ex);
        } finally {
        	try {
        		in.close();
        	} catch (IOException e) {
        		// ignore
        	}
        }
        return baos.toByteArray();
    }

    /**
     * copies in stream into out stream
     * 
     * @param in
     * @param out
     * @return
     * @throws IOException
     */
    public static long copy(InputStream in, OutputStream out) throws IOException {
    	long bytesCopied = 0;
        byte[] buffer = new byte[8192];

        int bytes = in.read(buffer);
        while (bytes >= 0) {
        	out.write(buffer, 0, bytes);
        	bytesCopied += bytes;
        	bytes = in.read(buffer);
		}

        return bytesCopied;
    }
    
    public static void writeText(File file, String text) throws IOException {
    	writeText(new FileWriter(file), text);
    }
    
    public static void writeText(Writer out, String text) throws IOException {
    	try {
    		out.write(text);
	    } finally {
	    	out.close();
	    }
    }
}
