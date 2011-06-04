package hudson.util;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.EmptyVisitor;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * Reflection information of a {@link Class}.
 *
 * @author Kohsuke Kawaguchi
 */
final class ClassDescriptor {

    private static final Logger LOGGER = Logger.getLogger(ClassDescriptor.class.getName());
    private static final String[] EMPTY_ARRAY = new String[0];
    public final Class clazz;
    public final FunctionList methods;
    public final Field[] fields;

    /**
     * @param clazz
     *      The class to build a descriptor around.
     * @param wrappers
     *      Optional wrapper duck-typing classes.
     *      Static methods on this class that has the first parameter
     *      as 'clazz' will be handled as if it's instance methods on
     *      'clazz'. Useful for adding view/controller methods on
     *      model classes.
     */
    public ClassDescriptor(Class clazz, Class... wrappers) {
        this.clazz = clazz;
        this.fields = clazz.getFields();

        // instance methods
        List<Function> functions = new ArrayList<Function>();
        for (Method m : clazz.getMethods()) {
            functions.add(new Function.InstanceFunction(m).protectBy(m));
        }
        if (wrappers != null) {
            for (Class w : wrappers) {
                for (Method m : w.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers())) {
                        continue;
                    }
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 0) {
                        continue;
                    }
                    if (p[0].isAssignableFrom(clazz)) {
                        continue;
                    }
                    functions.add(new Function.StaticFunction(m).protectBy(m));
                }
            }
        }
        this.methods = new FunctionList(functions);
    }

    /**
     * Loads the list of parameter names of the given method, by using a stapler-specific way of getting it.
     *
     * <p>
     * This is not the best place to expose this, but for now this would do.
     */
    public static String[] loadParameterNames(Method m) {
        CapturedParameterNames cpn = m.getAnnotation(CapturedParameterNames.class);
        if (cpn != null) {
            return cpn.value();
        }

        // debug information, if present, is more trustworthy
        try {
            String[] n = ASM.loadParametersFromAsm(m);
            if (n != null) {
                return n;
            }
        } catch (LinkageError e) {
            LOGGER.log(FINE, "Incompatible ASM", e);
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to load a class file", e);
        }

        // otherwise check the .stapler file
        Class<?> c = m.getDeclaringClass();
        URL url = c.getClassLoader().getResource(
                c.getName().replace('.', '/').replace('$', '/') + '/' + m.getName() + ".stapler");
        if (url != null) {
            try {
                return IOUtils.toString(url.openStream()).split(",");
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to load " + url, e);
                return EMPTY_ARRAY;
            }
        }

        // couldn't find it
        return EMPTY_ARRAY;
    }

    /**
     * Loads the list of parameter names of the given method, by using a stapler-specific way of getting it.
     *
     * <p>
     * This is not the best place to expose this, but for now this would do.
     */
    public static String[] loadParameterNames(Constructor<?> m) {
        CapturedParameterNames cpn = m.getAnnotation(CapturedParameterNames.class);
        if (cpn != null) {
            return cpn.value();
        }

        // debug information, if present, is more trustworthy
        try {
            String[] n = ASM.loadParametersFromAsm(m);
            if (n != null) {
                return n;
            }
        } catch (LinkageError e) {
            LOGGER.log(FINE, "Incompatible ASM", e);
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to load a class file", e);
        }

        // couldn't find it
        return EMPTY_ARRAY;
    }

    /**
     * Isolate the ASM dependency to its own class, as otherwise this seems to cause linkage error on the whole {@link ClassDescriptor}.
     */
    private static class ASM {

        /**
         * Try to load parameter names from the debug info by using ASM.
         */
        private static String[] loadParametersFromAsm(final Method m) throws IOException {
            final String[] paramNames = new String[m.getParameterTypes().length];
            if (paramNames.length == 0) {
                return paramNames;
            }
            Class<?> c = m.getDeclaringClass();
            URL clazz = c.getClassLoader().getResource(c.getName().replace('.', '/') + ".class");
            if (clazz == null) {
                return null;
            }

            final TreeMap<Integer, String> localVars = new TreeMap<Integer, String>();
            ClassReader r = new ClassReader(clazz.openStream());
            r.accept(new EmptyVisitor() {

                final String md = Type.getMethodDescriptor(m);
                // First localVariable is "this" for non-static method
                final int limit = (m.getModifiers() & Modifier.STATIC) != 0 ? 0 : 1;

                @Override
                public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
                    if (methodName.equals(m.getName()) && desc.equals(md)) {
                        return new EmptyVisitor() {

                            @Override
                            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                                if (index >= limit) {
                                    localVars.put(index, name);
                                }
                            }
                        };
                    } else {
                        return null; // ignore this method
                    }
                }
            }, false);

            // Indexes may not be sequential, but first set of local variables are method params
            int i = 0;
            for (String s : localVars.values()) {
                paramNames[i] = s;
                if (++i == paramNames.length) {
                    return paramNames;
                }
            }
            return null; // Not enough data found to fill array
        }

        /**
         * Try to load parameter names from the debug info by using ASM.
         */
        private static String[] loadParametersFromAsm(final Constructor m) throws IOException {
            final String[] paramNames = new String[m.getParameterTypes().length];
            if (paramNames.length == 0) {
                return paramNames;
            }
            Class<?> c = m.getDeclaringClass();
            URL clazz = c.getClassLoader().getResource(c.getName().replace('.', '/') + ".class");
            if (clazz == null) {
                return null;
            }

            final TreeMap<Integer, String> localVars = new TreeMap<Integer, String>();
            ClassReader r = new ClassReader(clazz.openStream());
            r.accept(new EmptyVisitor() {

                final String md = getConstructorDescriptor(m);

                public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
                    if (methodName.equals("<init>") && desc.equals(md)) {
                        return new EmptyVisitor() {

                            @Override
                            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                                if (index > 0) // 0 is 'this'
                                {
                                    localVars.put(index, name);
                                }
                            }
                        };
                    } else {
                        return null; // ignore this method
                    }
                }
            }, false);

            // Indexes may not be sequential, but first set of local variables are method params
            int i = 0;
            for (String s : localVars.values()) {
                paramNames[i] = s;
                if (++i == paramNames.length) {
                    return paramNames;
                }
            }
            return null; // Not enough data found to fill array
        }

        private static String getConstructorDescriptor(Constructor c) {
            StringBuilder buf = new StringBuilder("(");
            for (Class p : c.getParameterTypes()) {
                buf.append(Type.getDescriptor(p));
            }
            return buf.append(")V").toString();
        }
    }
}
