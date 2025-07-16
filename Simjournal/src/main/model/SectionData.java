package main.model;

class SectionData {
    String sectionTitle;
    java.util.List<String> tasks = new java.util.ArrayList<>();
    
    public SectionData(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }
}
