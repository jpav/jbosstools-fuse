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
package org.fusesource.ide.generator;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class CamelHeaderModelGenerator {
    private static final transient Logger LOG = LoggerFactory.getLogger(CamelHeaderModelGenerator.class);
    private final String outputDir;

    public static void main(String[] args) {
        try {
            String dir = "js";
            if (args.length > 0) {
                dir = args[0];
            }
            CamelHeaderModelGenerator generator = new CamelHeaderModelGenerator(dir);
            generator.run();
        } catch (Exception e) {
            LOG.error("Caught: " + e, e);
        }
    }

    public CamelHeaderModelGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    public void run() throws Exception {
        File file = new File(outputDir, "camelHeaderSchema.ts");
        PrintWriter out = new PrintWriter(new FileWriter(file));
        out.print("module Camel {\n"
                + "  // NOTE this file is code generated by the ide-codegen module in Fuse IDE\n"
                + "  export var camelHeaderSchema = {\n"
                + "    definitions: {\n"
                + "      headers: {\n"
                + "        properties: {");

        boolean first = true;
        try {
            Class<?> clazz = Exchange.class;
            Field[] declaredFields = clazz.getDeclaredFields();
            for (Field field : declaredFields) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && field.getType()
                        .equals(String.class)) {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof String) {
                        String propertyName = value.toString();
                        String typeName = "java.lang.String";

                        if (first) {
                            first = false;
                            out.println("");
                        } else {
                            out.println(",");
                        }
                        out.print("          \"" + propertyName + "\": {\n"
                                + "            type: \"" + typeName + "\"\n"
                                + "          }");

                    }
                }
            }
            out.println("\n"
                    + "        }\n"
                    + "      }\n"
                    + "    }\n"
                    + "  };\n"
                    + "}\n");
            System.out.println("Generated Camel Header schema " + file.getAbsolutePath());
        } finally {
            out.close();
        }
    }
}
