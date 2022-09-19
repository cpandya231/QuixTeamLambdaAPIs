package com.quixteam.usersapi.util;

import java.util.Collection;

public class LambdaUtil {
    public static boolean isEmptyCollection(Collection<?> collection) {
        return null == collection || collection.size() == 0;
    }

}
