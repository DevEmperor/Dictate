package net.devemperor.dictate.settings;

public class HowToItem {
    private final String text;
    private final int imageResId;

    public HowToItem(String text, int imageResId) {
        this.text = text;
        this.imageResId = imageResId;
    }

    public String getText() {
        return text;
    }

    public int getImageResId() {
        return imageResId;
    }
}

