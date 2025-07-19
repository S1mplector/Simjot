package mindmap;

import java.io.Serializable;
import java.util.ArrayList;

public class MindMap implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private final ArrayList<MindMapNode> nodes = new ArrayList<>();
    private final ArrayList<MindMapRelation> relations = new ArrayList<>();

    public MindMap(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public ArrayList<MindMapNode> getNodes() {
        return nodes;
    }

    public ArrayList<MindMapRelation> getRelations() {
        return relations;
    }
}
