package net.devemperor.dictate.rewording;

public class PromptModel {
    int id;
    int pos;
    String name;
    String prompt;
    boolean requiresSelection;

    public PromptModel(int id, int pos, String name, String prompt, boolean requiresSelection) {
        this.id = id;
        this.pos = pos;
        this.name = name;
        this.prompt = prompt;
        this.requiresSelection = requiresSelection;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean requiresSelection() {
        return requiresSelection;
    }

    public void setRequiresSelection(boolean requiresSelection) {
        this.requiresSelection = requiresSelection;
    }
}
