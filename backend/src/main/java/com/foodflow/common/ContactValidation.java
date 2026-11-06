package com.foodflow.common;

/** Format checks only; no verification of ownership or delivery. */
public final class ContactValidation {
    private ContactValidation() { }

    public static String phoneError(String value) {
        String phone = value == null ? "" : value.strip();
        String digits = phone.replaceAll("[^0-9]", "");
        if (phone.length() > 25 || digits.length() < 10 || digits.length() > 15 || !phone.matches("[0-9+() -]+")) {
            return "Телефон должен содержать от 10 до 15 цифр; допустимы + ( ) - и пробелы";
        }
        if (digits.chars().distinct().count() == 1) return "Номер не может состоять из одной повторяющейся цифры";
        return null;
    }

    public static String contactsError(String value) {
        if (value == null || value.isBlank() || value.length() > 500) return "Укажите телефон или email, не больше 500 символов";
        for (String part : value.split("[;,\\n]", -1)) {
            String contact = part.strip();
            boolean valid = contact.contains("@")
                    ? contact.length() <= 254 && contact.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                    : phoneError(contact) == null;
            if (!valid) return "Укажите корректный телефон или email; несколько контактов разделяйте точкой с запятой";
        }
        return null;
    }
}
