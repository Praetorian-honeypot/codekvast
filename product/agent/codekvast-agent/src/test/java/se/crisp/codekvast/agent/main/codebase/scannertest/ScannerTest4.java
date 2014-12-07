package se.crisp.codekvast.agent.main.codebase.scannertest;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Olle Hallin
 */
@SuppressWarnings("UnusedDeclaration")
@Slf4j
public class ScannerTest4 extends java.util.Date {
    @Override
    public long getTime() {
        return super.getTime() - 1;
    }

    public void m4(int i) {
        System.out.printf("m4(int)");
    }

    public void m4(long l) {
        System.out.printf("m4(long)");
    }

    public void m4(String s) {
        System.out.printf("m4(String)");
    }
}

