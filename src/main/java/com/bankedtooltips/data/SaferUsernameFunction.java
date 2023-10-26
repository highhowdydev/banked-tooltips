package com.bankedtooltips.data;

import com.google.common.base.Strings;
public class SaferUsernameFunction {
    String from(String username) {
        int start = Math.min(3, username.length() / 3);
        return username.substring(0, start) + Strings.repeat("-", username.length() - start);
    }
}
