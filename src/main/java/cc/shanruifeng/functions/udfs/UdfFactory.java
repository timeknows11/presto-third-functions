package cc.shanruifeng.functions.udfs;

import com.facebook.presto.metadata.FunctionFactory;
import com.facebook.presto.metadata.FunctionListBuilder;
import com.facebook.presto.metadata.SqlAggregationFunction;
import com.facebook.presto.metadata.SqlFunction;
import com.facebook.presto.operator.aggregation.AggregationFunction;
import com.facebook.presto.operator.window.WindowFunction;
import com.facebook.presto.spi.type.TypeManager;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author ruifeng.shan
 * @date 2016-07-06
 * @time 18:42
 */
public class UdfFactory implements FunctionFactory {
    private static final Logger log = Logger.get(UdfFactory.class);
    private final TypeManager typeManager;

    public UdfFactory(TypeManager tm) {
        this.typeManager = tm;
    }

    public List<SqlFunction> listFunctions() {
        FunctionListBuilder builder = new FunctionListBuilder(typeManager);
        try {
            List<Class<?>> classes = getFunctionClasses();
            addFunctions(builder, classes);
        } catch (IOException e) {
            System.out.println("Could not load classes from jar file: " + e);
            return ImmutableList.of();
        }

        return builder.getFunctions();
    }

    private void addFunctions(FunctionListBuilder builder, List<Class<?>> classes) {
        for (Class<?> clazz : classes) {
            log.info("Adding: " + clazz);
            if (SqlAggregationFunction.class.isAssignableFrom(clazz)) {
                try {
                    builder.function((SqlAggregationFunction) clazz.newInstance());
                } catch (InstantiationException e) {
                    log.info(String.format("Could not add %s, exception: %s, stack: %s", clazz.getName(), e, e.getStackTrace()));
                } catch (IllegalAccessException e) {
                    log.info(String.format("Could not add %s, exception: %s, stack: %s", clazz.getName(), e, e.getStackTrace()));
                }
            } else {
                if (clazz.getName().startsWith("cc.shanruifeng.functions.udfs.scalar")) {
                    try {
                        builder.scalar(clazz);
                    } catch (Exception e) {
                        if (e.getCause() instanceof IllegalAccessException) {
                            // This is alright, must be helper classes
                        } else {
                            log.info(String.format("Could not add %s, exception: %s, stack: %s", clazz.getName(), e, e.getStackTrace()));
                        }
                    }
                } else if (clazz.getName().startsWith("cc.shanruifeng.functions.udfs.aggregation")) {
                    AggregationFunction aggregationAnnotation = clazz.getAnnotation(AggregationFunction.class);
                    if (aggregationAnnotation == null) {
                        continue;
                    }
                    try {
                        builder.aggregate(clazz);
                    } catch (Exception e) {
                        log.info(String.format("Could not add %s, exception: %s, stack: %s", clazz.getName(), e, e.getStackTrace()));
                    }
                } else if (clazz.getName().startsWith("cc.shanruifeng.functions.udfs.window")) {
                    if (WindowFunctionDefinition.class.isAssignableFrom(clazz)) {
                        try {
                            WindowFunctionDefinition def = (WindowFunctionDefinition) clazz.newInstance();
                            builder.window(def.getName(), def.getReturnType(), def.getArgumentTypes(), (Class<? extends WindowFunction>) clazz);
                        } catch (InstantiationException e) {
                            log.info(String.format("Could not add %s, exception: %s, stack: %s", clazz.getName(), e, e.getStackTrace()));
                        } catch (IllegalAccessException e) {
                            log.info(String.format("Could not add %s, exception: %s, stack: %s", clazz.getName(), e, e.getStackTrace()));
                        }
                    }

                }

            }
        }
    }

    private List<Class<?>> getFunctionClasses()
            throws IOException {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        String classResource = this.getClass().getName().replace(".", "/") + ".class";
        String jarURLFile = Thread.currentThread().getContextClassLoader().getResource(classResource).getFile();
        int jarEnd = jarURLFile.indexOf('!');
        String jarLocation = jarURLFile.substring(0, jarEnd); // This is in URL format, convert once more to get actual file location
        jarLocation = new URL(jarLocation).getFile();

        List<String> classNames = new ArrayList<String>();
        ZipInputStream zip = new ZipInputStream(new FileInputStream(jarLocation));
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                String className = entry.getName().replace("/", "."); // This still has .class at the end
                className = className.substring(0, className.length() - 6); // remvove .class from end
                try {
                    classes.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    System.out.println(String.format("Could not load class %s, Exception: %s", className, e));
                    //TODO: add log
                }
            }
        }
        return classes;
    }
}