// Generated from org\jsont\grammar\JsonT.g4 by ANTLR 4.13.0
package org.jsont.grammar;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class JsonTLexer extends Lexer {
    public static final int
            SCHEMAS = 1, DATA = 2, DATA_SCHEMA = 3, ENUMS = 4, LB = 5, RB = 6, LA = 7, RA = 8, LP = 9,
            RP = 10, COLON = 11, COMMA = 12, LT = 13, GT = 14, QMARK = 15, BOOLEAN = 16, NULL = 17,
            NUMBER = 18, STRING = 19, IDENT = 20, WS = 21;
    public static final String[] ruleNames = makeRuleNames();
    /**
     * @deprecated Use {@link #VOCABULARY} instead.
     */
    @Deprecated
    public static final String[] tokenNames;
    public static final String _serializedATN =
            "\u0004\u0000\u0015\u00a6\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002" +
                    "\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002" +
                    "\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002" +
                    "\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002" +
                    "\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e" +
                    "\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011" +
                    "\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014" +
                    "\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000" +
                    "\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001" +
                    "\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002" +
                    "\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002" +
                    "\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003" +
                    "\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0006" +
                    "\u0001\u0006\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0001" +
                    "\n\u0001\n\u0001\u000b\u0001\u000b\u0001\f\u0001\f\u0001\r\u0001\r\u0001" +
                    "\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001" +
                    "\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0003\u000fj\b" +
                    "\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0003" +
                    "\u0010q\b\u0010\u0001\u0011\u0003\u0011t\b\u0011\u0001\u0011\u0004\u0011" +
                    "w\b\u0011\u000b\u0011\f\u0011x\u0001\u0011\u0001\u0011\u0004\u0011}\b" +
                    "\u0011\u000b\u0011\f\u0011~\u0003\u0011\u0081\b\u0011\u0001\u0012\u0001" +
                    "\u0012\u0001\u0012\u0001\u0012\u0005\u0012\u0087\b\u0012\n\u0012\f\u0012" +
                    "\u008a\t\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012" +
                    "\u0005\u0012\u0091\b\u0012\n\u0012\f\u0012\u0094\t\u0012\u0001\u0012\u0003" +
                    "\u0012\u0097\b\u0012\u0001\u0013\u0001\u0013\u0005\u0013\u009b\b\u0013" +
                    "\n\u0013\f\u0013\u009e\t\u0013\u0001\u0014\u0004\u0014\u00a1\b\u0014\u000b" +
                    "\u0014\f\u0014\u00a2\u0001\u0014\u0001\u0014\u0000\u0000\u0015\u0001\u0001" +
                    "\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006\r\u0007\u000f" +
                    "\b\u0011\t\u0013\n\u0015\u000b\u0017\f\u0019\r\u001b\u000e\u001d\u000f" +
                    "\u001f\u0010!\u0011#\u0012%\u0013\'\u0014)\u0015\u0001\u0000\u0006\u0001" +
                    "\u000009\u0002\u0000\"\"\\\\\u0002\u0000\'\'\\\\\u0003\u0000AZ__az\u0004" +
                    "\u000009AZ__az\u0003\u0000\t\n\r\r  \u00b2\u0000\u0001\u0001\u0000\u0000" +
                    "\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001\u0000\u0000" +
                    "\u0000\u0000\u0007\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000\u0000" +
                    "\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000\u0000" +
                    "\u000f\u0001\u0000\u0000\u0000\u0000\u0011\u0001\u0000\u0000\u0000\u0000" +
                    "\u0013\u0001\u0000\u0000\u0000\u0000\u0015\u0001\u0000\u0000\u0000\u0000" +
                    "\u0017\u0001\u0000\u0000\u0000\u0000\u0019\u0001\u0000\u0000\u0000\u0000" +
                    "\u001b\u0001\u0000\u0000\u0000\u0000\u001d\u0001\u0000\u0000\u0000\u0000" +
                    "\u001f\u0001\u0000\u0000\u0000\u0000!\u0001\u0000\u0000\u0000\u0000#\u0001" +
                    "\u0000\u0000\u0000\u0000%\u0001\u0000\u0000\u0000\u0000\'\u0001\u0000" +
                    "\u0000\u0000\u0000)\u0001\u0000\u0000\u0000\u0001+\u0001\u0000\u0000\u0000" +
                    "\u00033\u0001\u0000\u0000\u0000\u00058\u0001\u0000\u0000\u0000\u0007D" +
                    "\u0001\u0000\u0000\u0000\tJ\u0001\u0000\u0000\u0000\u000bL\u0001\u0000" +
                    "\u0000\u0000\rN\u0001\u0000\u0000\u0000\u000fP\u0001\u0000\u0000\u0000" +
                    "\u0011R\u0001\u0000\u0000\u0000\u0013T\u0001\u0000\u0000\u0000\u0015V" +
                    "\u0001\u0000\u0000\u0000\u0017X\u0001\u0000\u0000\u0000\u0019Z\u0001\u0000" +
                    "\u0000\u0000\u001b\\\u0001\u0000\u0000\u0000\u001d^\u0001\u0000\u0000" +
                    "\u0000\u001fi\u0001\u0000\u0000\u0000!p\u0001\u0000\u0000\u0000#s\u0001" +
                    "\u0000\u0000\u0000%\u0096\u0001\u0000\u0000\u0000\'\u0098\u0001\u0000" +
                    "\u0000\u0000)\u00a0\u0001\u0000\u0000\u0000+,\u0005s\u0000\u0000,-\u0005" +
                    "c\u0000\u0000-.\u0005h\u0000\u0000./\u0005e\u0000\u0000/0\u0005m\u0000" +
                    "\u000001\u0005a\u0000\u000012\u0005s\u0000\u00002\u0002\u0001\u0000\u0000" +
                    "\u000034\u0005d\u0000\u000045\u0005a\u0000\u000056\u0005t\u0000\u0000" +
                    "67\u0005a\u0000\u00007\u0004\u0001\u0000\u0000\u000089\u0005d\u0000\u0000" +
                    "9:\u0005a\u0000\u0000:;\u0005t\u0000\u0000;<\u0005a\u0000\u0000<=\u0005" +
                    "-\u0000\u0000=>\u0005s\u0000\u0000>?\u0005c\u0000\u0000?@\u0005h\u0000" +
                    "\u0000@A\u0005e\u0000\u0000AB\u0005m\u0000\u0000BC\u0005a\u0000\u0000" +
                    "C\u0006\u0001\u0000\u0000\u0000DE\u0005e\u0000\u0000EF\u0005n\u0000\u0000" +
                    "FG\u0005u\u0000\u0000GH\u0005m\u0000\u0000HI\u0005s\u0000\u0000I\b\u0001" +
                    "\u0000\u0000\u0000JK\u0005{\u0000\u0000K\n\u0001\u0000\u0000\u0000LM\u0005" +
                    "}\u0000\u0000M\f\u0001\u0000\u0000\u0000NO\u0005[\u0000\u0000O\u000e\u0001" +
                    "\u0000\u0000\u0000PQ\u0005]\u0000\u0000Q\u0010\u0001\u0000\u0000\u0000" +
                    "RS\u0005(\u0000\u0000S\u0012\u0001\u0000\u0000\u0000TU\u0005)\u0000\u0000" +
                    "U\u0014\u0001\u0000\u0000\u0000VW\u0005:\u0000\u0000W\u0016\u0001\u0000" +
                    "\u0000\u0000XY\u0005,\u0000\u0000Y\u0018\u0001\u0000\u0000\u0000Z[\u0005" +
                    "<\u0000\u0000[\u001a\u0001\u0000\u0000\u0000\\]\u0005>\u0000\u0000]\u001c" +
                    "\u0001\u0000\u0000\u0000^_\u0005?\u0000\u0000_\u001e\u0001\u0000\u0000" +
                    "\u0000`a\u0005t\u0000\u0000ab\u0005r\u0000\u0000bc\u0005u\u0000\u0000" +
                    "cj\u0005e\u0000\u0000de\u0005f\u0000\u0000ef\u0005a\u0000\u0000fg\u0005" +
                    "l\u0000\u0000gh\u0005s\u0000\u0000hj\u0005e\u0000\u0000i`\u0001\u0000" +
                    "\u0000\u0000id\u0001\u0000\u0000\u0000j \u0001\u0000\u0000\u0000kl\u0005" +
                    "n\u0000\u0000lm\u0005u\u0000\u0000mn\u0005l\u0000\u0000nq\u0005l\u0000" +
                    "\u0000oq\u0005\u2205\u0000\u0000pk\u0001\u0000\u0000\u0000po\u0001\u0000" +
                    "\u0000\u0000q\"\u0001\u0000\u0000\u0000rt\u0005-\u0000\u0000sr\u0001\u0000" +
                    "\u0000\u0000st\u0001\u0000\u0000\u0000tv\u0001\u0000\u0000\u0000uw\u0007" +
                    "\u0000\u0000\u0000vu\u0001\u0000\u0000\u0000wx\u0001\u0000\u0000\u0000" +
                    "xv\u0001\u0000\u0000\u0000xy\u0001\u0000\u0000\u0000y\u0080\u0001\u0000" +
                    "\u0000\u0000z|\u0005.\u0000\u0000{}\u0007\u0000\u0000\u0000|{\u0001\u0000" +
                    "\u0000\u0000}~\u0001\u0000\u0000\u0000~|\u0001\u0000\u0000\u0000~\u007f" +
                    "\u0001\u0000\u0000\u0000\u007f\u0081\u0001\u0000\u0000\u0000\u0080z\u0001" +
                    "\u0000\u0000\u0000\u0080\u0081\u0001\u0000\u0000\u0000\u0081$\u0001\u0000" +
                    "\u0000\u0000\u0082\u0088\u0005\"\u0000\u0000\u0083\u0084\u0005\\\u0000" +
                    "\u0000\u0084\u0087\t\u0000\u0000\u0000\u0085\u0087\b\u0001\u0000\u0000" +
                    "\u0086\u0083\u0001\u0000\u0000\u0000\u0086\u0085\u0001\u0000\u0000\u0000" +
                    "\u0087\u008a\u0001\u0000\u0000\u0000\u0088\u0086\u0001\u0000\u0000\u0000" +
                    "\u0088\u0089\u0001\u0000\u0000\u0000\u0089\u008b\u0001\u0000\u0000\u0000" +
                    "\u008a\u0088\u0001\u0000\u0000\u0000\u008b\u0097\u0005\"\u0000\u0000\u008c" +
                    "\u0092\u0005\'\u0000\u0000\u008d\u008e\u0005\\\u0000\u0000\u008e\u0091" +
                    "\t\u0000\u0000\u0000\u008f\u0091\b\u0002\u0000\u0000\u0090\u008d\u0001" +
                    "\u0000\u0000\u0000\u0090\u008f\u0001\u0000\u0000\u0000\u0091\u0094\u0001" +
                    "\u0000\u0000\u0000\u0092\u0090\u0001\u0000\u0000\u0000\u0092\u0093\u0001" +
                    "\u0000\u0000\u0000\u0093\u0095\u0001\u0000\u0000\u0000\u0094\u0092\u0001" +
                    "\u0000\u0000\u0000\u0095\u0097\u0005\'\u0000\u0000\u0096\u0082\u0001\u0000" +
                    "\u0000\u0000\u0096\u008c\u0001\u0000\u0000\u0000\u0097&\u0001\u0000\u0000" +
                    "\u0000\u0098\u009c\u0007\u0003\u0000\u0000\u0099\u009b\u0007\u0004\u0000" +
                    "\u0000\u009a\u0099\u0001\u0000\u0000\u0000\u009b\u009e\u0001\u0000\u0000" +
                    "\u0000\u009c\u009a\u0001\u0000\u0000\u0000\u009c\u009d\u0001\u0000\u0000" +
                    "\u0000\u009d(\u0001\u0000\u0000\u0000\u009e\u009c\u0001\u0000\u0000\u0000" +
                    "\u009f\u00a1\u0007\u0005\u0000\u0000\u00a0\u009f\u0001\u0000\u0000\u0000" +
                    "\u00a1\u00a2\u0001\u0000\u0000\u0000\u00a2\u00a0\u0001\u0000\u0000\u0000" +
                    "\u00a2\u00a3\u0001\u0000\u0000\u0000\u00a3\u00a4\u0001\u0000\u0000\u0000" +
                    "\u00a4\u00a5\u0006\u0014\u0000\u0000\u00a5*\u0001\u0000\u0000\u0000\u000e" +
                    "\u0000ipsx~\u0080\u0086\u0088\u0090\u0092\u0096\u009c\u00a2\u0001\u0006" +
                    "\u0000\u0000";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());
    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    private static final String[] _LITERAL_NAMES = makeLiteralNames();
    private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
    public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);
    public static String[] channelNames = {
            "DEFAULT_TOKEN_CHANNEL", "HIDDEN"
    };
    public static String[] modeNames = {
            "DEFAULT_MODE"
    };

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

    public JsonTLexer(CharStream input) {
        super(input);
        _interp = new LexerATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    private static String[] makeRuleNames() {
        return new String[]{
                "SCHEMAS", "DATA", "DATA_SCHEMA", "ENUMS", "LB", "RB", "LA", "RA", "LP",
                "RP", "COLON", "COMMA", "LT", "GT", "QMARK", "BOOLEAN", "NULL", "NUMBER",
                "STRING", "IDENT", "WS"
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
    public String[] getChannelNames() {
        return channelNames;
    }

    @Override
    public String[] getModeNames() {
        return modeNames;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }
}