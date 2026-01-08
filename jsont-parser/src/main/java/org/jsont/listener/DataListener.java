package org.jsont.listener;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

public class DataListener implements ParseTreeListener {
    @Override
    public void visitTerminal(TerminalNode node) {
//        System.out.println(node.getText());
    }

    @Override
    public void visitErrorNode(ErrorNode node) {
//        System.err.println(node.getText());
    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
//        System.out.println("Entered: " + ctx.getText());
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
//        System.out.println("Exited: " + ctx.getText());
    }
}
