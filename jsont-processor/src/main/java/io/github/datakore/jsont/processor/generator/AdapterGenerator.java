package io.github.datakore.jsont.processor.generator;

import io.github.datakore.jsont.processor.model.AnnoFieldModel;
import io.github.datakore.jsont.processor.model.AnnoTypeModel;
import io.github.datakore.jsont.processor.model.FieldMeta;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import javax.annotation.processing.Filer;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

public class AdapterGenerator {
    public static void generate(AnnoTypeModel model,
                                Filer filer,
                                Elements elements) throws IOException {
        String adapterName = String.format("%sSchemaAdapter", model.modelName());
        JavaFileObject file = filer.createSourceFile(String.format("%s.%s", model.packageName(), adapterName));
        try (Writer writer = file.openWriter()) {
            generate(model, writer);
        }
    }

    private static Map.Entry<String, String> handleProperType(String fqType, Set<String> imports) {
        // java.util.List<java.lang.String>
        String fqClass = null;
        String componentClass = null;
        if (fqType.contains("<")) {
            fqClass = fqType.substring(0, fqType.indexOf("<"));
            componentClass = fqType.substring(fqType.indexOf("<") + 1, fqType.indexOf(">"));
        } else {
            fqClass = fqType;
        }
        int lastDot = fqClass.lastIndexOf(".");
        if (lastDot != -1) {
            if (!fqClass.startsWith("java.lang")) imports.add(fqClass);
            fqClass = fqClass.substring(lastDot + 1);
        }
        if (componentClass != null) {
            lastDot = componentClass.lastIndexOf(".");
            if (lastDot != -1) {
                if (!componentClass.startsWith("java.lang")) imports.add(componentClass);
                componentClass = componentClass.substring(lastDot + 1);
            }
        }
        String finalComponentClass = componentClass;
        String finalFqClass = fqClass;
        return new Map.Entry<>() {
            @Override
            public String getKey() {
                return finalFqClass;
            }

            @Override
            public String getValue() {
                return finalComponentClass;
            }

            @Override
            public String setValue(String value) {
                throw new UnsupportedOperationException("Not allowed");
            }
        };
    }

    public static void generate(AnnoTypeModel typeModel, Writer writer) throws IOException {
        String pkg = typeModel.packageName();
        String modelName = typeModel.modelName();

        STGroup group = new STGroupFile("templates/schema-adapter.stg");
        ST st = group.getInstanceOf("adapter");
        // 2. Setup standard attributes
        st.add("packageName", pkg);

        st.add("modelName", modelName);

        // 3. Add imports dynamically
        Set<String> imports = new HashSet<>();
        imports.add("java.util.List");
        st.add("imports", imports);

        List<FieldMeta> fields = new ArrayList<>();

        for (AnnoFieldModel field : typeModel.fields()) {
            String jsonName = field.javaName();
            Map.Entry<String, String> type = handleProperType(field.declaredType(), imports);
            FieldMeta meta = new FieldMeta(field, type.getKey(), type.getValue());
            fields.add(meta);
        }

        st.add("fields", fields);
        st.add("subtypes", fields.stream()
                .filter(f -> f.getObject())
                .map(f -> f.getAfm().getDeclaredType())
                .map(clz -> deriveElementType(clz))
                .collect(Collectors.joining(",")));
        st.add("moreFields", fields.size() > 1);
        writer.write(st.render());
    }

    private static String deriveElementType(String clz) {
        String elementClass = null;
        if (clz.contains("<") && clz.contains(">")) {
            elementClass = clz.substring(clz.indexOf("<") + 1, clz.indexOf(">"));
            elementClass = elementClass.concat(".class");
        }
        return elementClass;
    }

}
