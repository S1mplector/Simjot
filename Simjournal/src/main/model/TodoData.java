package main.model;

class DayTodoData {
    String dateString; // e.g. "Mon, Feb 7"
    java.util.List<SectionData> sections = new java.util.ArrayList<>();
    
    public DayTodoData(String dateString) {
        this.dateString = dateString;
    }	
}