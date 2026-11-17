package com.sicost.permission;

import java.util.*;
import java.util.function.Supplier;
import static com.sicost.permission.PermissionService.*;

/** Server-only upper bound of a genuinely accepted user task; later grants cannot expand it. */
public final class PermissionAcceptanceContext {
    private static final ThreadLocal<Capabilities> ACCEPTED=new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERIOD=new ThreadLocal<>();
    private PermissionAcceptanceContext() {}
    public static <T> T run(Capabilities accepted,Supplier<T> work) {
        Objects.requireNonNull(accepted,"Accepted permissions are required");
        Capabilities previous=ACCEPTED.get();
        try {ACCEPTED.set(previous==null?accepted:intersect(previous,accepted));return work.get();}
        finally {if(previous==null)ACCEPTED.remove();else ACCEPTED.set(previous);}
    }
    public static Capabilities bound(Capabilities current) {
        Capabilities accepted=ACCEPTED.get();return accepted==null?current:intersect(accepted,current);
    }
    public static boolean active() {return ACCEPTED.get()!=null;}
    public static String fromMonth(String fallback) {return PERIOD.get()==null?fallback:PERIOD.get().get(0);}
    public static String toMonth(String fallback) {return PERIOD.get()==null?fallback:PERIOD.get().get(1);}
    public static <T> T period(java.time.YearMonth from,java.time.YearMonth to,Supplier<T> work) {
        var previous=PERIOD.get();
        try {PERIOD.set(List.of(from.toString().replace("-",""),to.toString().replace("-","")));return work.get();}
        finally {if(previous==null)PERIOD.remove();else PERIOD.set(previous);}
    }
    private static <T> Set<T> intersection(Set<T> left,Set<T> right) {Set<T> result=new HashSet<>(left);result.retainAll(right);return Set.copyOf(result);}
    private static Map<String,Set<String>> intersect(Map<String,Set<String>> left,Map<String,Set<String>> right) {
        Map<String,Set<String>> result=new LinkedHashMap<>();right.forEach((key,value)->{if(left.containsKey(key))result.put(key,intersection(left.get(key),value));});return Collections.unmodifiableMap(result);
    }
    private static Capabilities intersect(Capabilities accepted,Capabilities current) {
        Scope a=accepted.scope(),b=current.scope();
        Scope scope=a.all()?b:b.all()?a:new Scope(false,intersection(a.partIds(),b.partIds()),intersection(a.projectIds(),b.projectIds()));
        Map<String,Boolean> features=new HashMap<>();current.features().forEach((key,value)->features.put(key,value&&accepted.features().getOrDefault(key,false)));
        Set<String> pcOnly=new HashSet<>(accepted.pcOnlyScreens());pcOnly.addAll(current.pcOnlyScreens());
        return new Capabilities(intersect(accepted.screens(),current.screens()),scope,accepted.sensitive()&&current.sensitive(),intersect(accepted.fields(),current.fields()),Map.copyOf(features),Set.copyOf(pcOnly));
    }
}
