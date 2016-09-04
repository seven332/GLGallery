package com.hippo.glgallery;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String SOURCE_FILE = "../library/src/main/java-gen/com/hippo/glgallery/Postman.java";

    private static final String METHOD_CONSTRUCTOR =
            "Postman(GalleryView galleryView) {\n" +
            "    mGalleryView = galleryView;\n" +
            "}";

    private static final String METHOD_POST_METHOD =
            "void postMethod(int method, Object... args) {\n" +
            "    synchronized (this) {\n" +
            "        mMethodList.add(method);\n" +
            "        mArgsList.add(args);\n" +
            "    }\n" +
            "    mGalleryView.invalidate();\n" +
            "}";

    private static final String METHOD_DISPATCH_METHOD_PART1 =
            "void dispatchMethod() {\n" +
            "    final List<Integer> methodList = mMethodListTemp;\n" +
            "    final List<Object[]> argsList = mArgsListTemp;\n" +
            "    synchronized (this) {\n" +
            "        if (mMethodList.isEmpty()) {\n" +
            "            return;\n" +
            "        }\n" +
            "        methodList.addAll(mMethodList);\n" +
            "        argsList.addAll(mArgsList);\n" +
            "        mMethodList.clear();\n" +
            "        mArgsList.clear();\n" +
            "    }\n" +
            "    for (int i = 0, n = methodList.size(); i < n; i++) {\n" +
            "        final int method = methodList.get(i);\n" +
            "        final Object[] args = argsList.get(i);\n" +
            "        switch (method) {\n";

    private static final String METHOD_DISPATCH_METHOD_PART2 =
            "            default:\n" +
            "                throw new IllegalStateException(\"Unknown method: \" + method);\n" +
            "        }\n" +
            "    }\n" +
            "    methodList.clear();\n" +
            "    argsList.clear();\n" +
            "}";


    private static final String[][] METHOD_ARRAY = {
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

    public static void main(String[] args) throws IOException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
        javaClass.setPackage("com.hippo.glgallery");
        javaClass.addImport(List.class);
        javaClass.addImport(ArrayList.class);
        javaClass.setPackagePrivate();
        javaClass.setName("Postman");

        javaClass.addField("private static final int INIT_SIZE = 5");
        javaClass.addField("private final List<Integer> mMethodList = new ArrayList<>(INIT_SIZE)");
        javaClass.addField("private final List<Object[]> mArgsList = new ArrayList<>(INIT_SIZE)");
        javaClass.addField("private final List<Integer> mMethodListTemp = new ArrayList<>(INIT_SIZE)");
        javaClass.addField("private final List<Object[]> mArgsListTemp = new ArrayList<>(INIT_SIZE)");
        javaClass.addField("private final GalleryView mGalleryView");

        for (int i = 0; i < METHOD_ARRAY.length; i++) {
            final String[] method = METHOD_ARRAY[i];
            javaClass.addField()
                    .setPackagePrivate()
                    .setStatic(true)
                    .setFinal(true)
                    .setName(getMethodIdName(method))
                    .setType(int.class)
                    .setLiteralInitializer(Integer.toString(i));
        }

        javaClass.addMethod(METHOD_CONSTRUCTOR).setConstructor(true);
        javaClass.addMethod(METHOD_POST_METHOD);

        final StringBuilder methodDispatchMethod = new StringBuilder(METHOD_DISPATCH_METHOD_PART1);
        for (final String[] method : METHOD_ARRAY) {
            methodDispatchMethod.append("            case ").append(getMethodIdName(method)).append(":\n");
            methodDispatchMethod.append("                mGalleryView.").append(method[0]).append("Internal(");
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
        methodDispatchMethod.append(METHOD_DISPATCH_METHOD_PART2);
        javaClass.addMethod(methodDispatchMethod.toString());

        final File file = new File(SOURCE_FILE);
        new File(file.getParent()).mkdirs();
        final FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
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
