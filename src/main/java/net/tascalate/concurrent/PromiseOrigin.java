package net.tascalate.concurrent;

import java.util.EnumSet;
import java.util.Set;

public enum PromiseOrigin {
    THIS, PARAM;
    
    public static Set<PromiseOrigin> ALL = EnumSet.of(THIS, PARAM);
    public static Set<PromiseOrigin> NONE = EnumSet.noneOf(PromiseOrigin.class);
    public static Set<PromiseOrigin> THIS_ONLY = EnumSet.of(THIS);
    public static Set<PromiseOrigin> PARAM_ONLY = EnumSet.of(PARAM);
}
