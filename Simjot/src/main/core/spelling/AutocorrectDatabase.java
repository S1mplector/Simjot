/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.spelling;

/**
 * AutocorrectDatabase - Common typo corrections and contractions.
 */
public final class AutocorrectDatabase {
    private AutocorrectDatabase() {}
    
    public static String[][] getMappings() {
        return MAPPINGS;
    }
    
    private static final String[][] MAPPINGS = {
        {"teh", "the"}, {"hte", "the"}, {"taht", "that"}, {"adn", "and"}, {"nad", "and"},
        {"ahve", "have"}, {"hvae", "have"}, {"waht", "what"}, {"whta", "what"}, {"wiht", "with"},
        {"wtih", "with"}, {"fo", "of"}, {"ot", "to"}, {"ti", "it"}, {"si", "is"}, {"jsut", "just"},
        {"dont", "don't"}, {"cant", "can't"}, {"wont", "won't"}, {"didnt", "didn't"},
        {"doesnt", "doesn't"}, {"isnt", "isn't"}, {"wasnt", "wasn't"}, {"werent", "weren't"},
        {"havent", "haven't"}, {"hasnt", "hasn't"}, {"hadnt", "hadn't"}, {"wouldnt", "wouldn't"},
        {"couldnt", "couldn't"}, {"shouldnt", "shouldn't"}, {"youre", "you're"}, {"theyre", "they're"},
        {"hes", "he's"}, {"shes", "she's"}, {"thats", "that's"}, {"whats", "what's"},
        {"whos", "who's"}, {"im", "I'm"}, {"ive", "I've"}, {"ill", "I'll"}, {"id", "I'd"},
        {"youd", "you'd"}, {"theyd", "they'd"}, {"wed", "we'd"}, {"itll", "it'll"},
        {"youll", "you'll"}, {"theyll", "they'll"}, {"becuase", "because"}, {"beacuse", "because"},
        {"recieve", "receive"}, {"beleive", "believe"}, {"occured", "occurred"}, {"untill", "until"},
        {"tommorow", "tomorrow"}, {"tommorrow", "tomorrow"}, {"definately", "definitely"},
        {"definitly", "definitely"}, {"seperate", "separate"}, {"accomodate", "accommodate"},
        {"occassion", "occasion"}, {"occurence", "occurrence"}, {"neccessary", "necessary"},
        {"necessery", "necessary"}, {"wierd", "weird"}, {"thier", "their"}, {"freind", "friend"},
        {"buisness", "business"}, {"goverment", "government"}, {"enviroment", "environment"},
        {"knowlege", "knowledge"}, {"begining", "beginning"}, {"occuring", "occurring"},
        {"refering", "referring"}, {"writting", "writing"}, {"calender", "calendar"},
        {"adress", "address"}, {"aparent", "apparent"}, {"arguement", "argument"},
        {"assasinate", "assassinate"}, {"commitee", "committee"}, {"concious", "conscious"},
        {"existance", "existence"}, {"foriegn", "foreign"}, {"gaurd", "guard"}, {"harrass", "harass"},
        {"independant", "independent"}, {"judgement", "judgment"}, {"millenium", "millennium"},
        {"miniscule", "minuscule"}, {"mispell", "misspell"}, {"noticable", "noticeable"},
        {"parliment", "parliament"}, {"posession", "possession"}, {"privelege", "privilege"},
        {"pronounciation", "pronunciation"}, {"publically", "publicly"}, {"questionaire", "questionnaire"},
        {"recomend", "recommend"}, {"refrence", "reference"}, {"relevent", "relevant"},
        {"rythm", "rhythm"}, {"succesful", "successful"}, {"supercede", "supersede"},
        {"threshhold", "threshold"}, {"tyrany", "tyranny"}, {"vaccuum", "vacuum"},
        {"wether", "whether"}, {"whereever", "wherever"}, {"wich", "which"},
        {"accomodation", "accommodation"}, {"agressive", "aggressive"}, {"apparantly", "apparently"},
        {"basicly", "basically"}, {"completly", "completely"}, {"embarass", "embarrass"},
        {"exagerate", "exaggerate"}, {"generaly", "generally"}, {"geting", "getting"},
        {"hopefuly", "hopefully"}, {"imediately", "immediately"}, {"naturaly", "naturally"},
        {"ocasionally", "occasionally"}, {"originaly", "originally"}, {"particulary", "particularly"},
        {"probaly", "probably"}, {"realy", "really"}, {"similiar", "similar"}, {"sincerly", "sincerely"},
        {"specialy", "specially"}, {"technicaly", "technically"}, {"truely", "truly"},
        {"unfortunatly", "unfortunately"}, {"usualy", "usually"}, {"alot", "a lot"},
        {"alright", "all right"}, {"anyways", "anyway"}, {"irregardless", "regardless"},
        {"supposably", "supposedly"}, {"conversate", "converse"}, {"orientate", "orient"},
        {"preventative", "preventive"}, {"anywheres", "anywhere"}, {"nowheres", "nowhere"},
        {"somewheres", "somewhere"}, {"everywere", "everywhere"}, {"peice", "piece"},
        {"beleif", "belief"}, {"cheif", "chief"}, {"feild", "field"}, {"reciept", "receipt"},
        {"seize", "seize"}, {"wierd", "weird"}, {"yeild", "yield"}, {"acheive", "achieve"},
        {"aquire", "acquire"}, {"arguement", "argument"}, {"begining", "beginning"},
        {"bizzare", "bizarre"}, {"buisness", "business"}, {"camoflage", "camouflage"},
        {"carribean", "Caribbean"}, {"catagory", "category"}, {"cemetary", "cemetery"},
        {"changable", "changeable"}, {"collegue", "colleague"}, {"comming", "coming"},
        {"concensus", "consensus"}, {"coperate", "cooperate"}, {"copywrite", "copyright"},
        {"correspondance", "correspondence"}, {"desparate", "desperate"}, {"develope", "develop"},
        {"dilemna", "dilemma"}, {"disapear", "disappear"}, {"disapoint", "disappoint"},
        {"embarras", "embarrass"}, {"excede", "exceed"}, {"existance", "existence"},
        {"experiance", "experience"}, {"facinate", "fascinate"}, {"familar", "familiar"},
        {"finaly", "finally"}, {"flourescent", "fluorescent"}, {"forteen", "fourteen"},
        {"fourty", "forty"}, {"geneology", "genealogy"}, {"glamourous", "glamorous"},
        {"goverment", "government"}, {"grammer", "grammar"}, {"greatful", "grateful"},
        {"guarentee", "guarantee"}, {"happend", "happened"}, {"harrassment", "harassment"},
        {"heighth", "height"}, {"heros", "heroes"}, {"hygeine", "hygiene"}, {"ignorence", "ignorance"},
        {"immitate", "imitate"}, {"innoculate", "inoculate"}, {"inteligence", "intelligence"},
        {"intresting", "interesting"}, {"irresistable", "irresistible"}, {"knowlege", "knowledge"},
        {"liason", "liaison"}, {"libary", "library"}, {"lightening", "lightning"},
        {"maintainance", "maintenance"}, {"manuever", "maneuver"}, {"medeval", "medieval"},
        {"memento", "memento"}, {"milennium", "millennium"}, {"millionnaire", "millionaire"},
        {"mischevious", "mischievous"}, {"mispell", "misspell"}, {"momento", "memento"},
        {"neice", "niece"}, {"nieghbor", "neighbor"}, {"occassionally", "occasionally"},
        {"occurrance", "occurrence"}, {"offical", "official"}, {"ommision", "omission"},
        {"oppurtunity", "opportunity"}, {"optimisim", "optimism"}, {"outragous", "outrageous"},
        {"parliment", "parliament"}, {"passtime", "pastime"}, {"perseverence", "perseverance"},
        {"personell", "personnel"}, {"playwrite", "playwright"}, {"posession", "possession"},
        {"potatos", "potatoes"}, {"preceed", "precede"}, {"presance", "presence"},
        {"privelege", "privilege"}, {"procede", "proceed"}, {"professer", "professor"},
        {"progam", "program"}, {"pronounciation", "pronunciation"}, {"protege", "protege"},
        {"publically", "publicly"}, {"realy", "really"}, {"reccomend", "recommend"},
        {"reffered", "referred"}, {"relevent", "relevant"}, {"religous", "religious"},
        {"repitition", "repetition"}, {"resistence", "resistance"}, {"restaraunt", "restaurant"},
        {"rythm", "rhythm"}, {"sacrilegious", "sacrilegious"}, {"scedule", "schedule"},
        {"seige", "siege"}, {"sentance", "sentence"}, {"sieze", "seize"}, {"similiar", "similar"},
        {"speach", "speech"}, {"strenght", "strength"}, {"succede", "succeed"}, {"supercede", "supersede"},
        {"suprise", "surprise"}, {"tatoo", "tattoo"}, {"tendancy", "tendency"}, {"therefor", "therefore"},
        {"tomatos", "tomatoes"}, {"tounge", "tongue"}, {"truely", "truly"}, {"twelth", "twelfth"},
        {"tyrany", "tyranny"}, {"underate", "underrate"}, {"unfortunatly", "unfortunately"},
        {"vaccum", "vacuum"}, {"vegatable", "vegetable"}, {"vehical", "vehicle"}, {"visious", "vicious"},
        {"wether", "whether"}, {"wierd", "weird"}, {"writting", "writing"}
    };
}
