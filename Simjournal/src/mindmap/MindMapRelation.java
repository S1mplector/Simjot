package mindmap;

import java.io.Serializable;

public class MindMapRelation implements Serializable {
    private static final long serialVersionUID = 1L;

    public MindMapNode from, to;

    public MindMapRelation(MindMapNode from, MindMapNode to) {
        this.from = from;
        this.to = to;
    }
}
