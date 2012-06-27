/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl.rhino;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;

public class CoffeeScriptCompiler {
    private final Scriptable globalScope;

    public CoffeeScriptCompiler(ClassLoader classLoader) {
        InputStream inputStream = classLoader.getResourceAsStream("org/vertx/java/deploy/impl/rhino/coffee-script.js");
        
        try {
            Reader reader = new InputStreamReader(inputStream, "UTF-8");
            try {
                Context context = Context.enter();
                context.setOptimizationLevel(-1); // Without this, Rhino hits a 64K bytecode limit and fails
                try {
                    globalScope = context.initStandardObjects();
                    context.evaluateReader(globalScope, reader, "coffee-script.js", 0, null);
                } finally {
                    Context.exit();
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
            }
        }
    }

    public String compile (String coffeeScriptSource) throws JavaScriptException {
        Context context = Context.enter();
        try {
            Scriptable compileScope = context.newObject(globalScope);
            compileScope.setParentScope(globalScope);
            compileScope.put("coffeeScriptSource", compileScope, coffeeScriptSource);
            
            return (String)context.evaluateString(compileScope, 
                    String.format("CoffeeScript.compile(coffeeScriptSource);"),
                    "CoffeeScriptCompiler", 0, null);
        } finally {
            Context.exit();
        }
    }
    
    public static void main(String[] args) {
        CoffeeScriptCompiler c = new CoffeeScriptCompiler(CoffeeScriptCompiler.class.getClassLoader());
        String someCoffee = ""+ 
"fun = (a,b,c) ->\n"+
"  java.lang.System.out.println a+b+c\n" +
"\n" +
"fun 'hello','world','scott'"+
"";
        System.out.println(c.compile(someCoffee));
    }
}