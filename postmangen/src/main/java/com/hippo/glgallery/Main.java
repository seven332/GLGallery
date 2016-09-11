package com.hippo.glgallery;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {

    private static final String SOURCE_GALLERY_VIEW_POSTMAN_FILE =
            "../library/src/main/java-gen/com/hippo/glgallery/GalleryViewPostman.java";
    private static final String SOURCE_PROVIDER_ADAPTER_POSTMAN_FILE =
            "../library/src/main/java-gen/com/hippo/glgallery/ProviderAdapterPostman.java";

    private static final String METHOD_GALLERY_VIEW_POSTMAN_CONSTRUCTOR =
            "GalleryViewPostman(GalleryView galleryView) {\n" +
            "    mGalleryView = galleryView;\n" +
            "}";

    private static final String METHOD_PROVIDER_ADAPTER_POSTMAN_CONSTRUCTOR =
            "ProviderAdapterPostman(ProviderAdapter providerAdapter) {\n" +
            "    mProviderAdapter = providerAdapter;\n" +
            "}";

    private static final String METHOD_HANDLE_METHOD_PART1 =
            "@Override\n" +
            "protected void handleMethod(int method, Object... args) {\n" +
            "    switch (method) {\n";

    private static final String METHOD_HANDLE_METHOD_PART2 =
            "        default:\n" +
            "            throw new IllegalStateException(\"Unknown method: \" + method);\n" +
            "    }\n" +
            "}";

    private static final String[][] METHOD_GALLERY_VIEW_POSTMAN_ARRAY = {
            {"setLayoutMode", "Integer"},
            {"setScaleMode", "Integer"},
            {"setStartPosition", "Integer"},
            {"pageNext"},
            {"pagePrevious"},
            {"pageToId", "Integer"},
            {"scaleToNextLevel", "Float", "Float"},

            {"onSingleTapUp", "Float", "Float"},
            {"onSingleTapConfirmed", "Float", "Float"},
            {"onDoubleTap", "Float", "Float"},
            {"onDoubleTapConfirmed", "Float", "Float"},
            {"onLongPress", "Float", "Float"},
            {"onScroll", "Float", "Float", "Float", "Float", "Float", "Float"},
            {"onFling", "Float", "Float"},
            {"onScaleBegin", "Float", "Float"},
            {"onScale", "Float", "Float", "Float"},
            {"onScaleEnd"},
            {"onDown", "Float", "Float"},
            {"onUp"},
            {"onPointerDown", "Float", "Float"},
            {"onPointerUp"},
    };
    private static final String[][] METHOD_PROVIDER_ADAPTER_POSTMAN_ARRAY = {
            {"setClipMode", "Integer"},
            {"setShowIndex", "Boolean"},
    };

    public static void main(String[] args) throws IOException {
        genGalleryViewPostman();
        genProviderAdapterPostman();
    }

    private static void genGalleryViewPostman() throws IOException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
        javaClass.setPackage("com.hippo.glgallery");
        javaClass.setPackagePrivate();
        javaClass.setName("GalleryViewPostman");
        javaClass.setSuperType("Postman");

        javaClass.addField("private final GalleryView mGalleryView");
        javaClass.addMethod(METHOD_GALLERY_VIEW_POSTMAN_CONSTRUCTOR).setConstructor(true);
        addDispatchMethod(javaClass, METHOD_GALLERY_VIEW_POSTMAN_ARRAY, "mGalleryView");

        final File file = new File(SOURCE_GALLERY_VIEW_POSTMAN_FILE);
        new File(file.getParent()).mkdirs();
        final FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void genProviderAdapterPostman() throws IOException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
        javaClass.setPackage("com.hippo.glgallery");
        javaClass.setPackagePrivate();
        javaClass.setName("ProviderAdapterPostman");
        javaClass.setSuperType("Postman");

        javaClass.addField("private final ProviderAdapter mProviderAdapter");
        javaClass.addMethod(METHOD_PROVIDER_ADAPTER_POSTMAN_CONSTRUCTOR).setConstructor(true);
        addDispatchMethod(javaClass, METHOD_PROVIDER_ADAPTER_POSTMAN_ARRAY, "mProviderAdapter");

        final File file = new File(SOURCE_PROVIDER_ADAPTER_POSTMAN_FILE);
        new File(file.getParent()).mkdirs();
        final FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addDispatchMethod(JavaClassSource javaClass, String[][] methodArray, String obj) {
        for (int i = 0; i < methodArray.length; i++) {
            final String[] method = methodArray[i];
            javaClass.addField()
                    .setPackagePrivate()
                    .setStatic(true)
                    .setFinal(true)
                    .setName(getMethodIdName(method))
                    .setType(int.class)
                    .setLiteralInitializer(Integer.toString(i));
        }

        final StringBuilder methodDispatchMethod = new StringBuilder(METHOD_HANDLE_METHOD_PART1);
        for (final String[] method : methodArray) {
            methodDispatchMethod.append("            case ").append(getMethodIdName(method)).append(":\n");
            methodDispatchMethod.append("                ").append(obj).append(".").append(method[0]).append("Internal(");
            for (int i = 1; i < method.length; i++) {
                final String type = method[i];
                if (i != 1) {
                    methodDispatchMethod.append(", ");
                }
                methodDispatchMethod.append("(").append(type).append(") args[").append(i - 1).append("]");
            }
            methodDispatchMethod.append(");\n");
            methodDispatchMethod.append("                break;\n");
        }
        methodDispatchMethod.append(METHOD_HANDLE_METHOD_PART2);
        javaClass.addMethod(methodDispatchMethod.toString());
    }



    private static String getMethodIdName(String[] method) {
        final String methodName = method[0];
        final StringBuilder sb = new StringBuilder("METHOD_");

        for (int i = 0; i < methodName.length(); i++) {
            final char c = methodName.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_');
                sb.append(c);
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }

        return sb.toString();
    }
}
