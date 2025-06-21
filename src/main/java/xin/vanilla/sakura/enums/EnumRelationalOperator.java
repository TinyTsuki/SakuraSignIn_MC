package xin.vanilla.sakura.enums;

import lombok.Getter;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;

@Getter
public enum EnumRelationalOperator {
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    EQ("=="),
    NE("!="),
    ;

    private final String symbol;

    EnumRelationalOperator(String symbol) {
        this.symbol = symbol;
    }

    public boolean isValid(Object l, Object r) {
        boolean result = false;
        try {
            switch (this) {
                case LT: {
                    if (l instanceof Number && r instanceof Number) {
                        result = ((Number) l).doubleValue() < ((Number) r).doubleValue();
                    } else if (l instanceof Enum && r instanceof Enum) {
                        result = ((Enum<?>) l).ordinal() < ((Enum<?>) r).ordinal();
                    } else if (l instanceof Date && r instanceof Date) {
                        result = ((Date) l).before((Date) r);
                    }
                }
                break;
                case LE: {
                    if (l instanceof Number && r instanceof Number) {
                        result = ((Number) l).doubleValue() <= ((Number) r).doubleValue();
                    } else if (l instanceof Enum && r instanceof Enum) {
                        result = ((Enum<?>) l).ordinal() <= ((Enum<?>) r).ordinal();
                    } else if (l instanceof Date && r instanceof Date) {
                        result = ((Date) l).before((Date) r) || l.equals(r);
                    }

                }
                break;
                case GT: {
                    if (l instanceof Number && r instanceof Number) {
                        result = ((Number) l).doubleValue() > ((Number) r).doubleValue();
                    } else if (l instanceof Enum && r instanceof Enum) {
                        result = ((Enum<?>) l).ordinal() > ((Enum<?>) r).ordinal();
                    } else if (l instanceof Date && r instanceof Date) {
                        result = ((Date) l).after((Date) r);
                    }

                }
                break;
                case GE: {
                    if (l instanceof Number && r instanceof Number) {
                        result = ((Number) l).doubleValue() >= ((Number) r).doubleValue();
                    } else if (l instanceof Enum && r instanceof Enum) {
                        result = ((Enum<?>) l).ordinal() >= ((Enum<?>) r).ordinal();
                    } else if (l instanceof Date && r instanceof Date) {
                        result = ((Date) l).after((Date) r) || l.equals(r);
                    }

                }
                break;
                case EQ: {
                    if (l instanceof Number && r instanceof Number) {
                        result = ((Number) l).doubleValue() == ((Number) r).doubleValue();
                    } else if (l instanceof Enum && r instanceof Enum) {
                        result = ((Enum<?>) l).ordinal() == ((Enum<?>) r).ordinal();
                    } else if (l instanceof String && r instanceof String) {
                        result = ((String) l).equalsIgnoreCase((String) r);
                    } else if (l instanceof Boolean && r instanceof Boolean) {
                        result = l.equals(r);
                    } else if (l instanceof Collection && r instanceof Collection) {
                        result = ((Collection<?>) l).containsAll((Collection<?>) r) && ((Collection<?>) r).containsAll((Collection<?>) l);
                    } else if (l instanceof Array && r instanceof Array) {
                        result = Array.getLength(l) == Array.getLength(r);
                        for (int i = 0; i < Array.getLength(l); i++) {
                            if (!Array.get(l, i).equals(Array.get(r, i))) {
                                result = false;
                                break;
                            }
                        }
                    } else {
                        result = l.equals(r);
                    }
                }
                break;
                case NE: {
                    if (l instanceof Number && r instanceof Number) {
                        result = ((Number) l).doubleValue() != ((Number) r).doubleValue();
                    } else if (l instanceof Enum && r instanceof Enum) {
                        result = ((Enum<?>) l).ordinal() != ((Enum<?>) r).ordinal();
                    } else if (l instanceof String && r instanceof String) {
                        result = !((String) l).equalsIgnoreCase((String) r);
                    } else if (l instanceof Boolean && r instanceof Boolean) {
                        result = !l.equals(r);
                    } else if (l instanceof Collection && r instanceof Collection) {
                        result = !((Collection<?>) l).containsAll((Collection<?>) r) || !((Collection<?>) r).containsAll((Collection<?>) l);
                    } else if (l instanceof Array && r instanceof Array) {
                        result = Array.getLength(l) != Array.getLength(r);
                        for (int i = 0; i < Array.getLength(l); i++) {
                            if (Array.get(l, i).equals(Array.get(r, i))) {
                                result = false;
                                break;
                            }
                        }
                    } else {
                        result = !l.equals(r);
                    }
                }
                break;
                default:
                    return false;
            }
        } catch (Exception ignored) {
        }
        return result;
    }

}
