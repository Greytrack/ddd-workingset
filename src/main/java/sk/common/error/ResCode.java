package sk.common.error;

public interface ResCode {
    static <T extends Enum<T> & ResCode> String getValue(int key, Class<T> enumClass) {
        for (T value : enumClass.getEnumConstants()) {
            if (value.getCode() == key) {
                return value.getDesc();
            }
        }
        return null;
    }

    int getCode();

    String getDesc();
}
