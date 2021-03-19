package com.rohanprabhu.confinity;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

public class ConfinityPackager {
    public static void main(String[] args) {
        try {
            ByteArrayInputStream invokePayloadSer = new ByteArrayInputStream(Base64.getDecoder().decode(args[1]));

            Method invokeMethod = Arrays.stream(
                    Class.forName(args[0])
                        .getMethods()
            ).filter(x -> x.getName().equals("invoke"))
                    .collect(Collectors.toList())
                    .get(0);

            ObjectInputStream ois = new ObjectInputStream(invokePayloadSer);
            Object invokePayload = ois.readObject();

            Object target = Class.forName(args[0])
                    .getConstructor()
                    .newInstance();
            Object ret = invokeMethod.invoke(target, invokePayload);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream retOis = new ObjectOutputStream(baos);
            retOis.writeObject(ret);

            System.out.println(
                    "__CONF_BOUNDARY_" + Base64.getEncoder().encodeToString(baos.toByteArray()) + "_CONF_BOUNDARY__"
            );
        } catch (
                ClassNotFoundException | IOException |
                NoSuchMethodException | IllegalAccessException |
                InstantiationException | InvocationTargetException e
        ) {
            e.printStackTrace();
        }
    }
}
