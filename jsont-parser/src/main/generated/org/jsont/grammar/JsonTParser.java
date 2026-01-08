// Generated from org\jsont\grammar\JsonT.g4 by ANTLR 4.13.0
package org.jsont.grammar;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class JsonTParser extends Parser {
    public static final int
            SCHEMAS = 1, DATA = 2, DATA_SCHEMA = 3, ENUMS = 4, LB = 5, RB = 6, LA = 7, RA = 8, LP = 9,
            RP = 10, COLON = 11, COMMA = 12, LT = 13, GT = 14, QMARK = 15, BOOLEAN = 16, NULL = 17,
            NUMBER = 18, STRING = 19, IDENT = 20, WS = 21;
    public static final int
            RULE_jsonT = 0, RULE_catalog = 1, RULE_data = 2, RULE_schemasSection = 3,
            RULE_schemaEntry = 4, RULE_schemaNode = 5, RULE_fieldDecl = 6, RULE_optionalMark = 7,
            RULE_constraintsSection = 8, RULE_constraint = 9, RULE_constraintName = 10,
            RULE_constraintValue = 11, RULE_enumsSection = 12, RULE_enumDef = 13,
            RULE_enumBody = 14, RULE_enumValue = 15, RULE_dataSchemaSection = 16,
            RULE_dataSection = 17, RULE_dataRow = 18, RULE_value = 19, RULE_scalarValue = 20,
            RULE_objectValue = 21, RULE_arrayValue = 22, RULE_typeRef = 23, RULE_arraySuffix = 24;
    public static final String[] ruleNames = makeRuleNames();
    /**
     * @deprecated Use {@link #VOCABULARY} instead.
     */
    @Deprecated
    public static final String[] tokenNames;
    public static final String _serializedATN =
            "\u0004\u0001\u0015\u00e9\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001" +
                    "\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004" +
                    "\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007" +
                    "\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b" +
                    "\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007" +
                    "\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007" +
                    "\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007" +
                    "\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007" +
                    "\u0018\u0001\u0000\u0003\u00004\b\u0000\u0001\u0000\u0003\u00007\b\u0000" +
                    "\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001" +
                    "\u0003\u0001?\b\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002" +
                    "\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003" +
                    "\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003O\b\u0003" +
                    "\n\u0003\f\u0003R\t\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004" +
                    "\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005" +
                    "\u0005\u0005^\b\u0005\n\u0005\f\u0005a\t\u0005\u0001\u0005\u0001\u0005" +
                    "\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006i\b\u0006" +
                    "\u0001\u0006\u0001\u0006\u0003\u0006m\b\u0006\u0001\u0006\u0001\u0006" +
                    "\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0005\bv\b\b\n\b\f\b" +
                    "y\t\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b" +
                    "\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0005\f\u008a" +
                    "\b\f\n\f\f\f\u008d\t\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001\u000e" +
                    "\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u0098\b\u000e\n\u000e" +
                    "\f\u000e\u009b\t\u000e\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f" +
                    "\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011" +
                    "\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0005\u0011\u00ab\b\u0011" +
                    "\n\u0011\f\u0011\u00ae\t\u0011\u0001\u0011\u0001\u0011\u0001\u0012\u0001" +
                    "\u0012\u0001\u0012\u0001\u0012\u0005\u0012\u00b6\b\u0012\n\u0012\f\u0012" +
                    "\u00b9\t\u0012\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0013" +
                    "\u0003\u0013\u00c0\b\u0013\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015" +
                    "\u0001\u0015\u0001\u0015\u0005\u0015\u00c8\b\u0015\n\u0015\f\u0015\u00cb" +
                    "\t\u0015\u0001\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001" +
                    "\u0016\u0005\u0016\u00d3\b\u0016\n\u0016\f\u0016\u00d6\t\u0016\u0001\u0016" +
                    "\u0001\u0016\u0001\u0017\u0001\u0017\u0003\u0017\u00dc\b\u0017\u0001\u0017" +
                    "\u0001\u0017\u0001\u0017\u0001\u0017\u0003\u0017\u00e2\b\u0017\u0003\u0017" +
                    "\u00e4\b\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0000\u0000" +
                    "\u0019\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018" +
                    "\u001a\u001c\u001e \"$&(*,.0\u0000\u0002\u0002\u0000\u0010\u0010\u0012" +
                    "\u0013\u0001\u0000\u0010\u0014\u00e2\u00003\u0001\u0000\u0000\u0000\u0002" +
                    ":\u0001\u0000\u0000\u0000\u0004B\u0001\u0000\u0000\u0000\u0006H\u0001" +
                    "\u0000\u0000\u0000\bU\u0001\u0000\u0000\u0000\nY\u0001\u0000\u0000\u0000" +
                    "\fd\u0001\u0000\u0000\u0000\u000ep\u0001\u0000\u0000\u0000\u0010r\u0001" +
                    "\u0000\u0000\u0000\u0012z\u0001\u0000\u0000\u0000\u0014\u007f\u0001\u0000" +
                    "\u0000\u0000\u0016\u0081\u0001\u0000\u0000\u0000\u0018\u0083\u0001\u0000" +
                    "\u0000\u0000\u001a\u0090\u0001\u0000\u0000\u0000\u001c\u0093\u0001\u0000" +
                    "\u0000\u0000\u001e\u009e\u0001\u0000\u0000\u0000 \u00a0\u0001\u0000\u0000" +
                    "\u0000\"\u00a4\u0001\u0000\u0000\u0000$\u00b1\u0001\u0000\u0000\u0000" +
                    "&\u00bf\u0001\u0000\u0000\u0000(\u00c1\u0001\u0000\u0000\u0000*\u00c3" +
                    "\u0001\u0000\u0000\u0000,\u00ce\u0001\u0000\u0000\u0000.\u00e3\u0001\u0000" +
                    "\u0000\u00000\u00e5\u0001\u0000\u0000\u000024\u0003\u0002\u0001\u0000" +
                    "32\u0001\u0000\u0000\u000034\u0001\u0000\u0000\u000046\u0001\u0000\u0000" +
                    "\u000057\u0003\u0004\u0002\u000065\u0001\u0000\u0000\u000067\u0001\u0000" +
                    "\u0000\u000078\u0001\u0000\u0000\u000089\u0005\u0000\u0000\u00019\u0001" +
                    "\u0001\u0000\u0000\u0000:;\u0005\u0005\u0000\u0000;>\u0003\u0006\u0003" +
                    "\u0000<=\u0005\f\u0000\u0000=?\u0003\u0018\f\u0000><\u0001\u0000\u0000" +
                    "\u0000>?\u0001\u0000\u0000\u0000?@\u0001\u0000\u0000\u0000@A\u0005\u0006" +
                    "\u0000\u0000A\u0003\u0001\u0000\u0000\u0000BC\u0005\u0005\u0000\u0000" +
                    "CD\u0003 \u0010\u0000DE\u0005\f\u0000\u0000EF\u0003\"\u0011\u0000FG\u0005" +
                    "\u0006\u0000\u0000G\u0005\u0001\u0000\u0000\u0000HI\u0005\u0001\u0000" +
                    "\u0000IJ\u0005\u000b\u0000\u0000JK\u0005\u0005\u0000\u0000KP\u0003\b\u0004" +
                    "\u0000LM\u0005\f\u0000\u0000MO\u0003\b\u0004\u0000NL\u0001\u0000\u0000" +
                    "\u0000OR\u0001\u0000\u0000\u0000PN\u0001\u0000\u0000\u0000PQ\u0001\u0000" +
                    "\u0000\u0000QS\u0001\u0000\u0000\u0000RP\u0001\u0000\u0000\u0000ST\u0005" +
                    "\u0006\u0000\u0000T\u0007\u0001\u0000\u0000\u0000UV\u0005\u0014\u0000" +
                    "\u0000VW\u0005\u000b\u0000\u0000WX\u0003\n\u0005\u0000X\t\u0001\u0000" +
                    "\u0000\u0000YZ\u0005\u0005\u0000\u0000Z_\u0003\f\u0006\u0000[\\\u0005" +
                    "\f\u0000\u0000\\^\u0003\f\u0006\u0000][\u0001\u0000\u0000\u0000^a\u0001" +
                    "\u0000\u0000\u0000_]\u0001\u0000\u0000\u0000_`\u0001\u0000\u0000\u0000" +
                    "`b\u0001\u0000\u0000\u0000a_\u0001\u0000\u0000\u0000bc\u0005\u0006\u0000" +
                    "\u0000c\u000b\u0001\u0000\u0000\u0000de\u0003.\u0017\u0000ef\u0005\u000b" +
                    "\u0000\u0000fh\u0005\u0014\u0000\u0000gi\u0003\u000e\u0007\u0000hg\u0001" +
                    "\u0000\u0000\u0000hi\u0001\u0000\u0000\u0000ij\u0001\u0000\u0000\u0000" +
                    "jl\u0005\t\u0000\u0000km\u0003\u0010\b\u0000lk\u0001\u0000\u0000\u0000" +
                    "lm\u0001\u0000\u0000\u0000mn\u0001\u0000\u0000\u0000no\u0005\n\u0000\u0000" +
                    "o\r\u0001\u0000\u0000\u0000pq\u0005\u000f\u0000\u0000q\u000f\u0001\u0000" +
                    "\u0000\u0000rw\u0003\u0012\t\u0000st\u0005\f\u0000\u0000tv\u0003\u0012" +
                    "\t\u0000us\u0001\u0000\u0000\u0000vy\u0001\u0000\u0000\u0000wu\u0001\u0000" +
                    "\u0000\u0000wx\u0001\u0000\u0000\u0000x\u0011\u0001\u0000\u0000\u0000" +
                    "yw\u0001\u0000\u0000\u0000z{\u0003\u0014\n\u0000{|\u0005\t\u0000\u0000" +
                    "|}\u0003\u0016\u000b\u0000}~\u0005\n\u0000\u0000~\u0013\u0001\u0000\u0000" +
                    "\u0000\u007f\u0080\u0005\u0014\u0000\u0000\u0080\u0015\u0001\u0000\u0000" +
                    "\u0000\u0081\u0082\u0007\u0000\u0000\u0000\u0082\u0017\u0001\u0000\u0000" +
                    "\u0000\u0083\u0084\u0005\u0004\u0000\u0000\u0084\u0085\u0005\u000b\u0000" +
                    "\u0000\u0085\u0086\u0005\u0007\u0000\u0000\u0086\u008b\u0003\u001a\r\u0000" +
                    "\u0087\u0088\u0005\f\u0000\u0000\u0088\u008a\u0003\u001a\r\u0000\u0089" +
                    "\u0087\u0001\u0000\u0000\u0000\u008a\u008d\u0001\u0000\u0000\u0000\u008b" +
                    "\u0089\u0001\u0000\u0000\u0000\u008b\u008c\u0001\u0000\u0000\u0000\u008c" +
                    "\u008e\u0001\u0000\u0000\u0000\u008d\u008b\u0001\u0000\u0000\u0000\u008e" +
                    "\u008f\u0005\b\u0000\u0000\u008f\u0019\u0001\u0000\u0000\u0000\u0090\u0091" +
                    "\u0005\u0014\u0000\u0000\u0091\u0092\u0003\u001c\u000e\u0000\u0092\u001b" +
                    "\u0001\u0000\u0000\u0000\u0093\u0094\u0005\u0005\u0000\u0000\u0094\u0099" +
                    "\u0003\u001e\u000f\u0000\u0095\u0096\u0005\f\u0000\u0000\u0096\u0098\u0003" +
                    "\u001e\u000f\u0000\u0097\u0095\u0001\u0000\u0000\u0000\u0098\u009b\u0001" +
                    "\u0000\u0000\u0000\u0099\u0097\u0001\u0000\u0000\u0000\u0099\u009a\u0001" +
                    "\u0000\u0000\u0000\u009a\u009c\u0001\u0000\u0000\u0000\u009b\u0099\u0001" +
                    "\u0000\u0000\u0000\u009c\u009d\u0005\u0006\u0000\u0000\u009d\u001d\u0001" +
                    "\u0000\u0000\u0000\u009e\u009f\u0005\u0014\u0000\u0000\u009f\u001f\u0001" +
                    "\u0000\u0000\u0000\u00a0\u00a1\u0005\u0003\u0000\u0000\u00a1\u00a2\u0005" +
                    "\u000b\u0000\u0000\u00a2\u00a3\u0005\u0014\u0000\u0000\u00a3!\u0001\u0000" +
                    "\u0000\u0000\u00a4\u00a5\u0005\u0002\u0000\u0000\u00a5\u00a6\u0005\u000b" +
                    "\u0000\u0000\u00a6\u00a7\u0005\u0007\u0000\u0000\u00a7\u00ac\u0003$\u0012" +
                    "\u0000\u00a8\u00a9\u0005\f\u0000\u0000\u00a9\u00ab\u0003$\u0012\u0000" +
                    "\u00aa\u00a8\u0001\u0000\u0000\u0000\u00ab\u00ae\u0001\u0000\u0000\u0000" +
                    "\u00ac\u00aa\u0001\u0000\u0000\u0000\u00ac\u00ad\u0001\u0000\u0000\u0000" +
                    "\u00ad\u00af\u0001\u0000\u0000\u0000\u00ae\u00ac\u0001\u0000\u0000\u0000" +
                    "\u00af\u00b0\u0005\b\u0000\u0000\u00b0#\u0001\u0000\u0000\u0000\u00b1" +
                    "\u00b2\u0005\u0005\u0000\u0000\u00b2\u00b7\u0003&\u0013\u0000\u00b3\u00b4" +
                    "\u0005\f\u0000\u0000\u00b4\u00b6\u0003&\u0013\u0000\u00b5\u00b3\u0001" +
                    "\u0000\u0000\u0000\u00b6\u00b9\u0001\u0000\u0000\u0000\u00b7\u00b5\u0001" +
                    "\u0000\u0000\u0000\u00b7\u00b8\u0001\u0000\u0000\u0000\u00b8\u00ba\u0001" +
                    "\u0000\u0000\u0000\u00b9\u00b7\u0001\u0000\u0000\u0000\u00ba\u00bb\u0005" +
                    "\u0006\u0000\u0000\u00bb%\u0001\u0000\u0000\u0000\u00bc\u00c0\u0003(\u0014" +
                    "\u0000\u00bd\u00c0\u0003*\u0015\u0000\u00be\u00c0\u0003,\u0016\u0000\u00bf" +
                    "\u00bc\u0001\u0000\u0000\u0000\u00bf\u00bd\u0001\u0000\u0000\u0000\u00bf" +
                    "\u00be\u0001\u0000\u0000\u0000\u00c0\'\u0001\u0000\u0000\u0000\u00c1\u00c2" +
                    "\u0007\u0001\u0000\u0000\u00c2)\u0001\u0000\u0000\u0000\u00c3\u00c4\u0005" +
                    "\u0005\u0000\u0000\u00c4\u00c9\u0003&\u0013\u0000\u00c5\u00c6\u0005\f" +
                    "\u0000\u0000\u00c6\u00c8\u0003&\u0013\u0000\u00c7\u00c5\u0001\u0000\u0000" +
                    "\u0000\u00c8\u00cb\u0001\u0000\u0000\u0000\u00c9\u00c7\u0001\u0000\u0000" +
                    "\u0000\u00c9\u00ca\u0001\u0000\u0000\u0000\u00ca\u00cc\u0001\u0000\u0000" +
                    "\u0000\u00cb\u00c9\u0001\u0000\u0000\u0000\u00cc\u00cd\u0005\u0006\u0000" +
                    "\u0000\u00cd+\u0001\u0000\u0000\u0000\u00ce\u00cf\u0005\u0007\u0000\u0000" +
                    "\u00cf\u00d4\u0003&\u0013\u0000\u00d0\u00d1\u0005\f\u0000\u0000\u00d1" +
                    "\u00d3\u0003&\u0013\u0000\u00d2\u00d0\u0001\u0000\u0000\u0000\u00d3\u00d6" +
                    "\u0001\u0000\u0000\u0000\u00d4\u00d2\u0001\u0000\u0000\u0000\u00d4\u00d5" +
                    "\u0001\u0000\u0000\u0000\u00d5\u00d7\u0001\u0000\u0000\u0000\u00d6\u00d4" +
                    "\u0001\u0000\u0000\u0000\u00d7\u00d8\u0005\b\u0000\u0000\u00d8-\u0001" +
                    "\u0000\u0000\u0000\u00d9\u00db\u0005\u0014\u0000\u0000\u00da\u00dc\u0003" +
                    "0\u0018\u0000\u00db\u00da\u0001\u0000\u0000\u0000\u00db\u00dc\u0001\u0000" +
                    "\u0000\u0000\u00dc\u00e4\u0001\u0000\u0000\u0000\u00dd\u00de\u0005\r\u0000" +
                    "\u0000\u00de\u00df\u0005\u0014\u0000\u0000\u00df\u00e1\u0005\u000e\u0000" +
                    "\u0000\u00e0\u00e2\u00030\u0018\u0000\u00e1\u00e0\u0001\u0000\u0000\u0000" +
                    "\u00e1\u00e2\u0001\u0000\u0000\u0000\u00e2\u00e4\u0001\u0000\u0000\u0000" +
                    "\u00e3\u00d9\u0001\u0000\u0000\u0000\u00e3\u00dd\u0001\u0000\u0000\u0000" +
                    "\u00e4/\u0001\u0000\u0000\u0000\u00e5\u00e6\u0005\u0007\u0000\u0000\u00e6" +
                    "\u00e7\u0005\b\u0000\u0000\u00e71\u0001\u0000\u0000\u0000\u001236>P_h" +
                    "lw\u008b\u0099\u00ac\u00b7\u00bf\u00c9\u00d4\u00db\u00e1\u00e3";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());
    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    private static final String[] _LITERAL_NAMES = makeLiteralNames();
    private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
    public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

    static {
        RuntimeMetaData.checkVersion("4.13.0", RuntimeMetaData.VERSION);
    }

    static {
        tokenNames = new String[_SYMBOLIC_NAMES.length];
        for (int i = 0; i < tokenNames.length; i++) {
            tokenNames[i] = VOCABULARY.getLiteralName(i);
            if (tokenNames[i] == null) {
                tokenNames[i] = VOCABULARY.getSymbolicName(i);
            }

            if (tokenNames[i] == null) {
                tokenNames[i] = "<INVALID>";
            }
        }
    }

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }

    public JsonTParser(TokenStream input) {
        super(input);
        _interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    private static String[] makeRuleNames() {
        return new String[]{
                "jsonT", "catalog", "data", "schemasSection", "schemaEntry", "schemaNode",
                "fieldDecl", "optionalMark", "constraintsSection", "constraint", "constraintName",
                "constraintValue", "enumsSection", "enumDef", "enumBody", "enumValue",
                "dataSchemaSection", "dataSection", "dataRow", "value", "scalarValue",
                "objectValue", "arrayValue", "typeRef", "arraySuffix"
        };
    }

    private static String[] makeLiteralNames() {
        return new String[]{
                null, "'schemas'", "'data'", "'data-schema'", "'enums'", "'{'", "'}'",
                "'['", "']'", "'('", "')'", "':'", "','", "'<'", "'>'", "'?'"
        };
    }

    private static String[] makeSymbolicNames() {
        return new String[]{
                null, "SCHEMAS", "DATA", "DATA_SCHEMA", "ENUMS", "LB", "RB", "LA", "RA",
                "LP", "RP", "COLON", "COMMA", "LT", "GT", "QMARK", "BOOLEAN", "NULL",
                "NUMBER", "STRING", "IDENT", "WS"
        };
    }

    @Override
    @Deprecated
    public String[] getTokenNames() {
        return tokenNames;
    }

    @Override

    public Vocabulary getVocabulary() {
        return VOCABULARY;
    }

    @Override
    public String getGrammarFileName() {
        return "JsonT.g4";
    }

    @Override
    public String[] getRuleNames() {
        return ruleNames;
    }

    @Override
    public String getSerializedATN() {
        return _serializedATN;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    public final JsonTContext jsonT() throws RecognitionException {
        JsonTContext _localctx = new JsonTContext(_ctx, getState());
        enterRule(_localctx, 0, RULE_jsonT);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(51);
                _errHandler.sync(this);
                switch (getInterpreter().adaptivePredict(_input, 0, _ctx)) {
                    case 1: {
                        setState(50);
                        catalog();
                    }
                    break;
                }
                setState(54);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == LB) {
                    {
                        setState(53);
                        data();
                    }
                }

                setState(56);
                match(EOF);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final CatalogContext catalog() throws RecognitionException {
        CatalogContext _localctx = new CatalogContext(_ctx, getState());
        enterRule(_localctx, 2, RULE_catalog);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(58);
                match(LB);
                setState(59);
                schemasSection();
                setState(62);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == COMMA) {
                    {
                        setState(60);
                        match(COMMA);
                        setState(61);
                        enumsSection();
                    }
                }

                setState(64);
                match(RB);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final DataContext data() throws RecognitionException {
        DataContext _localctx = new DataContext(_ctx, getState());
        enterRule(_localctx, 4, RULE_data);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(66);
                match(LB);
                setState(67);
                dataSchemaSection();
                setState(68);
                match(COMMA);
                setState(69);
                dataSection();
                setState(70);
                match(RB);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final SchemasSectionContext schemasSection() throws RecognitionException {
        SchemasSectionContext _localctx = new SchemasSectionContext(_ctx, getState());
        enterRule(_localctx, 6, RULE_schemasSection);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(72);
                match(SCHEMAS);
                setState(73);
                match(COLON);
                setState(74);
                match(LB);
                setState(75);
                schemaEntry();
                setState(80);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(76);
                            match(COMMA);
                            setState(77);
                            schemaEntry();
                        }
                    }
                    setState(82);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(83);
                match(RB);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final SchemaEntryContext schemaEntry() throws RecognitionException {
        SchemaEntryContext _localctx = new SchemaEntryContext(_ctx, getState());
        enterRule(_localctx, 8, RULE_schemaEntry);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(85);
                match(IDENT);
                setState(86);
                match(COLON);
                setState(87);
                schemaNode();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final SchemaNodeContext schemaNode() throws RecognitionException {
        SchemaNodeContext _localctx = new SchemaNodeContext(_ctx, getState());
        enterRule(_localctx, 10, RULE_schemaNode);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(89);
                match(LB);
                setState(90);
                fieldDecl();
                setState(95);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(91);
                            match(COMMA);
                            setState(92);
                            fieldDecl();
                        }
                    }
                    setState(97);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(98);
                match(RB);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final FieldDeclContext fieldDecl() throws RecognitionException {
        FieldDeclContext _localctx = new FieldDeclContext(_ctx, getState());
        enterRule(_localctx, 12, RULE_fieldDecl);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(100);
                typeRef();
                setState(101);
                match(COLON);
                setState(102);
                match(IDENT);
                setState(104);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == QMARK) {
                    {
                        setState(103);
                        optionalMark();
                    }
                }

                setState(106);
                match(LP);
                setState(108);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == IDENT) {
                    {
                        setState(107);
                        constraintsSection();
                    }
                }

                setState(110);
                match(RP);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final OptionalMarkContext optionalMark() throws RecognitionException {
        OptionalMarkContext _localctx = new OptionalMarkContext(_ctx, getState());
        enterRule(_localctx, 14, RULE_optionalMark);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(112);
                match(QMARK);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ConstraintsSectionContext constraintsSection() throws RecognitionException {
        ConstraintsSectionContext _localctx = new ConstraintsSectionContext(_ctx, getState());
        enterRule(_localctx, 16, RULE_constraintsSection);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(114);
                constraint();
                setState(119);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(115);
                            match(COMMA);
                            setState(116);
                            constraint();
                        }
                    }
                    setState(121);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ConstraintContext constraint() throws RecognitionException {
        ConstraintContext _localctx = new ConstraintContext(_ctx, getState());
        enterRule(_localctx, 18, RULE_constraint);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(122);
                constraintName();
                setState(123);
                match(LP);
                setState(124);
                constraintValue();
                setState(125);
                match(RP);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ConstraintNameContext constraintName() throws RecognitionException {
        ConstraintNameContext _localctx = new ConstraintNameContext(_ctx, getState());
        enterRule(_localctx, 20, RULE_constraintName);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(127);
                match(IDENT);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ConstraintValueContext constraintValue() throws RecognitionException {
        ConstraintValueContext _localctx = new ConstraintValueContext(_ctx, getState());
        enterRule(_localctx, 22, RULE_constraintValue);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(129);
                _la = _input.LA(1);
                if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & 851968L) != 0))) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final EnumsSectionContext enumsSection() throws RecognitionException {
        EnumsSectionContext _localctx = new EnumsSectionContext(_ctx, getState());
        enterRule(_localctx, 24, RULE_enumsSection);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(131);
                match(ENUMS);
                setState(132);
                match(COLON);
                setState(133);
                match(LA);
                setState(134);
                enumDef();
                setState(139);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(135);
                            match(COMMA);
                            setState(136);
                            enumDef();
                        }
                    }
                    setState(141);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(142);
                match(RA);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final EnumDefContext enumDef() throws RecognitionException {
        EnumDefContext _localctx = new EnumDefContext(_ctx, getState());
        enterRule(_localctx, 26, RULE_enumDef);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(144);
                match(IDENT);
                setState(145);
                enumBody();
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final EnumBodyContext enumBody() throws RecognitionException {
        EnumBodyContext _localctx = new EnumBodyContext(_ctx, getState());
        enterRule(_localctx, 28, RULE_enumBody);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(147);
                match(LB);
                setState(148);
                enumValue();
                setState(153);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(149);
                            match(COMMA);
                            setState(150);
                            enumValue();
                        }
                    }
                    setState(155);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(156);
                match(RB);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final EnumValueContext enumValue() throws RecognitionException {
        EnumValueContext _localctx = new EnumValueContext(_ctx, getState());
        enterRule(_localctx, 30, RULE_enumValue);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(158);
                match(IDENT);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final DataSchemaSectionContext dataSchemaSection() throws RecognitionException {
        DataSchemaSectionContext _localctx = new DataSchemaSectionContext(_ctx, getState());
        enterRule(_localctx, 32, RULE_dataSchemaSection);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(160);
                match(DATA_SCHEMA);
                setState(161);
                match(COLON);
                setState(162);
                match(IDENT);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final DataSectionContext dataSection() throws RecognitionException {
        DataSectionContext _localctx = new DataSectionContext(_ctx, getState());
        enterRule(_localctx, 34, RULE_dataSection);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(164);
                match(DATA);
                setState(165);
                match(COLON);
                setState(166);
                match(LA);
                setState(167);
                dataRow();
                setState(172);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(168);
                            match(COMMA);
                            setState(169);
                            dataRow();
                        }
                    }
                    setState(174);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(175);
                match(RA);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final DataRowContext dataRow() throws RecognitionException {
        DataRowContext _localctx = new DataRowContext(_ctx, getState());
        enterRule(_localctx, 36, RULE_dataRow);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(177);
                match(LB);
                setState(178);
                value();
                setState(183);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(179);
                            match(COMMA);
                            setState(180);
                            value();
                        }
                    }
                    setState(185);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(186);
                match(RB);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ValueContext value() throws RecognitionException {
        ValueContext _localctx = new ValueContext(_ctx, getState());
        enterRule(_localctx, 38, RULE_value);
        try {
            setState(191);
            _errHandler.sync(this);
            switch (_input.LA(1)) {
                case BOOLEAN:
                case NULL:
                case NUMBER:
                case STRING:
                case IDENT:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(188);
                    scalarValue();
                }
                break;
                case LB:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(189);
                    objectValue();
                }
                break;
                case LA:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(190);
                    arrayValue();
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ScalarValueContext scalarValue() throws RecognitionException {
        ScalarValueContext _localctx = new ScalarValueContext(_ctx, getState());
        enterRule(_localctx, 40, RULE_scalarValue);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(193);
                _la = _input.LA(1);
                if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & 2031616L) != 0))) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ObjectValueContext objectValue() throws RecognitionException {
        ObjectValueContext _localctx = new ObjectValueContext(_ctx, getState());
        enterRule(_localctx, 42, RULE_objectValue);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(195);
                match(LB);
                setState(196);
                value();
                setState(201);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(197);
                            match(COMMA);
                            setState(198);
                            value();
                        }
                    }
                    setState(203);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(204);
                match(RB);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ArrayValueContext arrayValue() throws RecognitionException {
        ArrayValueContext _localctx = new ArrayValueContext(_ctx, getState());
        enterRule(_localctx, 44, RULE_arrayValue);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(206);
                match(LA);
                setState(207);
                value();
                setState(212);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == COMMA) {
                    {
                        {
                            setState(208);
                            match(COMMA);
                            setState(209);
                            value();
                        }
                    }
                    setState(214);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
                setState(215);
                match(RA);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final TypeRefContext typeRef() throws RecognitionException {
        TypeRefContext _localctx = new TypeRefContext(_ctx, getState());
        enterRule(_localctx, 46, RULE_typeRef);
        int _la;
        try {
            setState(227);
            _errHandler.sync(this);
            switch (_input.LA(1)) {
                case IDENT:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(217);
                    match(IDENT);
                    setState(219);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                    if (_la == LA) {
                        {
                            setState(218);
                            arraySuffix();
                        }
                    }

                }
                break;
                case LT:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(221);
                    match(LT);
                    setState(222);
                    match(IDENT);
                    setState(223);
                    match(GT);
                    setState(225);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                    if (_la == LA) {
                        {
                            setState(224);
                            arraySuffix();
                        }
                    }

                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public final ArraySuffixContext arraySuffix() throws RecognitionException {
        ArraySuffixContext _localctx = new ArraySuffixContext(_ctx, getState());
        enterRule(_localctx, 48, RULE_arraySuffix);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(229);
                match(LA);
                setState(230);
                match(RA);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class JsonTContext extends ParserRuleContext {
        public JsonTContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode EOF() {
            return getToken(JsonTParser.EOF, 0);
        }

        public CatalogContext catalog() {
            return getRuleContext(CatalogContext.class, 0);
        }

        public DataContext data() {
            return getRuleContext(DataContext.class, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_jsonT;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitJsonT(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class CatalogContext extends ParserRuleContext {
        public CatalogContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LB() {
            return getToken(JsonTParser.LB, 0);
        }

        public SchemasSectionContext schemasSection() {
            return getRuleContext(SchemasSectionContext.class, 0);
        }

        public TerminalNode RB() {
            return getToken(JsonTParser.RB, 0);
        }

        public TerminalNode COMMA() {
            return getToken(JsonTParser.COMMA, 0);
        }

        public EnumsSectionContext enumsSection() {
            return getRuleContext(EnumsSectionContext.class, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_catalog;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitCatalog(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class DataContext extends ParserRuleContext {
        public DataContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LB() {
            return getToken(JsonTParser.LB, 0);
        }

        public DataSchemaSectionContext dataSchemaSection() {
            return getRuleContext(DataSchemaSectionContext.class, 0);
        }

        public TerminalNode COMMA() {
            return getToken(JsonTParser.COMMA, 0);
        }

        public DataSectionContext dataSection() {
            return getRuleContext(DataSectionContext.class, 0);
        }

        public TerminalNode RB() {
            return getToken(JsonTParser.RB, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_data;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitData(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class SchemasSectionContext extends ParserRuleContext {
        public SchemasSectionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode SCHEMAS() {
            return getToken(JsonTParser.SCHEMAS, 0);
        }

        public TerminalNode COLON() {
            return getToken(JsonTParser.COLON, 0);
        }

        public TerminalNode LB() {
            return getToken(JsonTParser.LB, 0);
        }

        public List<SchemaEntryContext> schemaEntry() {
            return getRuleContexts(SchemaEntryContext.class);
        }

        public SchemaEntryContext schemaEntry(int i) {
            return getRuleContext(SchemaEntryContext.class, i);
        }

        public TerminalNode RB() {
            return getToken(JsonTParser.RB, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_schemasSection;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitSchemasSection(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class SchemaEntryContext extends ParserRuleContext {
        public SchemaEntryContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        public TerminalNode COLON() {
            return getToken(JsonTParser.COLON, 0);
        }

        public SchemaNodeContext schemaNode() {
            return getRuleContext(SchemaNodeContext.class, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_schemaEntry;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitSchemaEntry(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class SchemaNodeContext extends ParserRuleContext {
        public SchemaNodeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LB() {
            return getToken(JsonTParser.LB, 0);
        }

        public List<FieldDeclContext> fieldDecl() {
            return getRuleContexts(FieldDeclContext.class);
        }

        public FieldDeclContext fieldDecl(int i) {
            return getRuleContext(FieldDeclContext.class, i);
        }

        public TerminalNode RB() {
            return getToken(JsonTParser.RB, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_schemaNode;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitSchemaNode(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class FieldDeclContext extends ParserRuleContext {
        public FieldDeclContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TypeRefContext typeRef() {
            return getRuleContext(TypeRefContext.class, 0);
        }

        public TerminalNode COLON() {
            return getToken(JsonTParser.COLON, 0);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        public TerminalNode LP() {
            return getToken(JsonTParser.LP, 0);
        }

        public TerminalNode RP() {
            return getToken(JsonTParser.RP, 0);
        }

        public OptionalMarkContext optionalMark() {
            return getRuleContext(OptionalMarkContext.class, 0);
        }

        public ConstraintsSectionContext constraintsSection() {
            return getRuleContext(ConstraintsSectionContext.class, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_fieldDecl;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitFieldDecl(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class OptionalMarkContext extends ParserRuleContext {
        public OptionalMarkContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode QMARK() {
            return getToken(JsonTParser.QMARK, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_optionalMark;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitOptionalMark(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ConstraintsSectionContext extends ParserRuleContext {
        public ConstraintsSectionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public List<ConstraintContext> constraint() {
            return getRuleContexts(ConstraintContext.class);
        }

        public ConstraintContext constraint(int i) {
            return getRuleContext(ConstraintContext.class, i);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_constraintsSection;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor)
                return ((JsonTVisitor<? extends T>) visitor).visitConstraintsSection(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ConstraintContext extends ParserRuleContext {
        public ConstraintContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public ConstraintNameContext constraintName() {
            return getRuleContext(ConstraintNameContext.class, 0);
        }

        public TerminalNode LP() {
            return getToken(JsonTParser.LP, 0);
        }

        public ConstraintValueContext constraintValue() {
            return getRuleContext(ConstraintValueContext.class, 0);
        }

        public TerminalNode RP() {
            return getToken(JsonTParser.RP, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_constraint;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitConstraint(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ConstraintNameContext extends ParserRuleContext {
        public ConstraintNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_constraintName;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitConstraintName(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ConstraintValueContext extends ParserRuleContext {
        public ConstraintValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode NUMBER() {
            return getToken(JsonTParser.NUMBER, 0);
        }

        public TerminalNode STRING() {
            return getToken(JsonTParser.STRING, 0);
        }

        public TerminalNode BOOLEAN() {
            return getToken(JsonTParser.BOOLEAN, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_constraintValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor)
                return ((JsonTVisitor<? extends T>) visitor).visitConstraintValue(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class EnumsSectionContext extends ParserRuleContext {
        public EnumsSectionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode ENUMS() {
            return getToken(JsonTParser.ENUMS, 0);
        }

        public TerminalNode COLON() {
            return getToken(JsonTParser.COLON, 0);
        }

        public TerminalNode LA() {
            return getToken(JsonTParser.LA, 0);
        }

        public List<EnumDefContext> enumDef() {
            return getRuleContexts(EnumDefContext.class);
        }

        public EnumDefContext enumDef(int i) {
            return getRuleContext(EnumDefContext.class, i);
        }

        public TerminalNode RA() {
            return getToken(JsonTParser.RA, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_enumsSection;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitEnumsSection(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class EnumDefContext extends ParserRuleContext {
        public EnumDefContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        public EnumBodyContext enumBody() {
            return getRuleContext(EnumBodyContext.class, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_enumDef;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitEnumDef(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class EnumBodyContext extends ParserRuleContext {
        public EnumBodyContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LB() {
            return getToken(JsonTParser.LB, 0);
        }

        public List<EnumValueContext> enumValue() {
            return getRuleContexts(EnumValueContext.class);
        }

        public EnumValueContext enumValue(int i) {
            return getRuleContext(EnumValueContext.class, i);
        }

        public TerminalNode RB() {
            return getToken(JsonTParser.RB, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_enumBody;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitEnumBody(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class EnumValueContext extends ParserRuleContext {
        public EnumValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_enumValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitEnumValue(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class DataSchemaSectionContext extends ParserRuleContext {
        public DataSchemaSectionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode DATA_SCHEMA() {
            return getToken(JsonTParser.DATA_SCHEMA, 0);
        }

        public TerminalNode COLON() {
            return getToken(JsonTParser.COLON, 0);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_dataSchemaSection;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor)
                return ((JsonTVisitor<? extends T>) visitor).visitDataSchemaSection(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class DataSectionContext extends ParserRuleContext {
        public DataSectionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode DATA() {
            return getToken(JsonTParser.DATA, 0);
        }

        public TerminalNode COLON() {
            return getToken(JsonTParser.COLON, 0);
        }

        public TerminalNode LA() {
            return getToken(JsonTParser.LA, 0);
        }

        public List<DataRowContext> dataRow() {
            return getRuleContexts(DataRowContext.class);
        }

        public DataRowContext dataRow(int i) {
            return getRuleContext(DataRowContext.class, i);
        }

        public TerminalNode RA() {
            return getToken(JsonTParser.RA, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_dataSection;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitDataSection(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class DataRowContext extends ParserRuleContext {
        public DataRowContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LB() {
            return getToken(JsonTParser.LB, 0);
        }

        public List<ValueContext> value() {
            return getRuleContexts(ValueContext.class);
        }

        public ValueContext value(int i) {
            return getRuleContext(ValueContext.class, i);
        }

        public TerminalNode RB() {
            return getToken(JsonTParser.RB, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_dataRow;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitDataRow(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ValueContext extends ParserRuleContext {
        public ValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public ScalarValueContext scalarValue() {
            return getRuleContext(ScalarValueContext.class, 0);
        }

        public ObjectValueContext objectValue() {
            return getRuleContext(ObjectValueContext.class, 0);
        }

        public ArrayValueContext arrayValue() {
            return getRuleContext(ArrayValueContext.class, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_value;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitValue(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ScalarValueContext extends ParserRuleContext {
        public ScalarValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode STRING() {
            return getToken(JsonTParser.STRING, 0);
        }

        public TerminalNode NUMBER() {
            return getToken(JsonTParser.NUMBER, 0);
        }

        public TerminalNode BOOLEAN() {
            return getToken(JsonTParser.BOOLEAN, 0);
        }

        public TerminalNode NULL() {
            return getToken(JsonTParser.NULL, 0);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_scalarValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitScalarValue(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ObjectValueContext extends ParserRuleContext {
        public ObjectValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LB() {
            return getToken(JsonTParser.LB, 0);
        }

        public List<ValueContext> value() {
            return getRuleContexts(ValueContext.class);
        }

        public ValueContext value(int i) {
            return getRuleContext(ValueContext.class, i);
        }

        public TerminalNode RB() {
            return getToken(JsonTParser.RB, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_objectValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitObjectValue(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ArrayValueContext extends ParserRuleContext {
        public ArrayValueContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LA() {
            return getToken(JsonTParser.LA, 0);
        }

        public List<ValueContext> value() {
            return getRuleContexts(ValueContext.class);
        }

        public ValueContext value(int i) {
            return getRuleContext(ValueContext.class, i);
        }

        public TerminalNode RA() {
            return getToken(JsonTParser.RA, 0);
        }

        public List<TerminalNode> COMMA() {
            return getTokens(JsonTParser.COMMA);
        }

        public TerminalNode COMMA(int i) {
            return getToken(JsonTParser.COMMA, i);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arrayValue;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitArrayValue(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class TypeRefContext extends ParserRuleContext {
        public TypeRefContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode IDENT() {
            return getToken(JsonTParser.IDENT, 0);
        }

        public ArraySuffixContext arraySuffix() {
            return getRuleContext(ArraySuffixContext.class, 0);
        }

        public TerminalNode LT() {
            return getToken(JsonTParser.LT, 0);
        }

        public TerminalNode GT() {
            return getToken(JsonTParser.GT, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_typeRef;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitTypeRef(this);
            else return visitor.visitChildren(this);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public static class ArraySuffixContext extends ParserRuleContext {
        public ArraySuffixContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        public TerminalNode LA() {
            return getToken(JsonTParser.LA, 0);
        }

        public TerminalNode RA() {
            return getToken(JsonTParser.RA, 0);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arraySuffix;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof JsonTVisitor) return ((JsonTVisitor<? extends T>) visitor).visitArraySuffix(this);
            else return visitor.visitChildren(this);
        }
    }
}