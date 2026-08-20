package com.draftwa.mobile;

public final class RuleSimulation {
    private static int pass = 0, total = 0;
    private static void ok(String name, boolean condition) {
        total++;
        if (!condition) throw new AssertionError("FAIL: " + name);
        pass++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        ok("contains Soko", MessageRules.matches("Bonjour Soko test", "Soko"));
        ok("case insensitive", MessageRules.matches("bonjour SOKO", "soko"));
        ok("missing Soko", !MessageRules.matches("Bonjour test", "Soko"));
        ok("suffix only", MessageRules.matches("Bonjour Allo.", "; Allo."));
        ok("suffix only reject", !MessageRules.matches("Allo. suite", "; Allo."));
        ok("combined", MessageRules.matches("Soko message Allo.", "Soko ; Allo."));
        ok("combined reject contains", !MessageRules.matches("message Allo.", "Soko ; Allo."));
        ok("combined reject suffix", !MessageRules.matches("Soko message", "Soko ; Allo."));
        ok("empty condition accepts", MessageRules.matches("anything", ""));

        String a = MessageRules.transform("Bonjour Soko, demande ,Enregistré, prête", ",Enregistré,", false, "Africa/Abidjan", 5, 18);
        ok("remove token", a.equals("Bonjour Soko, demande prête"));
        String b = MessageRules.transform("Soko test Allo.", "; Allo.", false, "Africa/Abidjan", 5, 18);
        ok("remove suffix", b.equals("Soko test"));
        String c = MessageRules.transform("Soko foo Soko", "Soko", false, "Africa/Abidjan", 5, 18);
        ok("remove all case insensitive", c.equals("foo"));
        String d = MessageRules.transform("SOKO foo soko", "Soko", false, "Africa/Abidjan", 5, 18);
        ok("remove all mixed case", d.equals("foo"));
        String e = MessageRules.transform("texte,Enregistré,fin", ",Enregistré,", false, "Africa/Abidjan", 5, 18);
        ok("remove embedded exact token", e.equals("textefin"));
        ok("semicolon left only", MessageRules.matches("contains Soko", "Soko ;"));
        ok("semicolon empty both accepts", MessageRules.matches("x", ";"));
        String multi = MessageRules.transform("Bonjour Soko  ligne 1\n  ligne 2 ,Enregistré,", ",Enregistré,", false, "Africa/Abidjan", 5, 18);
        ok("preserve newline", multi.equals("Bonjour Soko ligne 1\nligne 2"));

        for (int i = 0; i < 5000; i++) {
            String m = "prefix" + i + " Soko ,Enregistré, suffix";
            okSilent(MessageRules.matches(m, "Soko"));
            String t = MessageRules.transform(m, ",Enregistré,", false, "Africa/Abidjan", 5, 18);
            okSilent(!t.toLowerCase().contains("enregistré"));
        }
        System.out.println("RESULT " + pass + "/" + total + " PASS");
    }

    private static void okSilent(boolean condition) {
        total++;
        if (!condition) throw new AssertionError("FUZZ FAIL at " + total);
        pass++;
    }
}
