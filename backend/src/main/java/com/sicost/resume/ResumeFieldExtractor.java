package com.sicost.resume;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;
import static com.sicost.resume.ResumeExtractionDtos.*;
import com.sicost.resume.ResumeExtractionDtos.Dictionary;

@Component
public class ResumeFieldExtractor {
    static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s.]","");}
    static String vendorKey(String text){return normalize(text).replace("주식회사","").replace("(주)","").replace("㈜","");}
    public Extraction extract(String text,String target,Dictionary dictionary,LocalDate today) {
        var out=new Output(target);if(text.isBlank())return out.finish(null);
        Map<String,Set<String>> labels=new LinkedHashMap<>();
        dictionary.codes().stream().filter(c->c.groupId().equals("RSM_LABEL")).forEach(c->labels.computeIfAbsent(normalize(c.name()),k->new LinkedHashSet<>()).add(c.addVal1()));
        Map<String,List<String>> fields=new HashMap<>();
        for(var entry:labels.entrySet())if(entry.getValue().size()>1)out.warnings.add("이름표 사전 충돌: "+entry.getKey()+". 해당 이름표는 자동 입력하지 않습니다.");
        for(String line:text.split("\\R")) {
            String[] cells=line.strip().split("[:：\\t]|\\u0007",-1);
            for(int index=0;index+1<cells.length;index++) {
                Set<String> mapped=labels.get(normalize(cells[index]));
                if(mapped==null||mapped.size()!=1)continue;String key=mapped.iterator().next();String value=cells[++index].strip();
                if(!value.isBlank())fields.computeIfAbsent(key,k->new ArrayList<>()).add(value);
            }
        }
        String name=unique(fields,"NAME","name",out);
        if(name!=null){String value=name.strip();if(value.matches("[가-힣](?:\\s+[가-힣])+"))value=value.replaceAll("\\s","");if(value.codePointCount(0,value.length())<=50)out.put("name",value,false,"");else out.invalid("name","이름이 50자를 넘습니다. 직접 확인해 주세요.");}
        if(target.equals("CANDIDATE")) {
            contacts(text,fields,out);
            String amount=unique(fields,"WANT","desiredRate",out);
            if(amount!=null){String value=amount.replaceAll("[,\\s]","");var match=Pattern.compile("^(?:월)?([0-9]+(?:\\.[0-9]+)?)(만원|원)(?:/월|월)?$").matcher(value);
                if(match.matches())try{BigDecimal n=new BigDecimal(match.group(1)).multiply(match.group(2).equals("만원")?new BigDecimal("10000"):BigDecimal.ONE);if(n.signum()<=0||n.compareTo(new BigDecimal("999999999999999"))>0)throw new ArithmeticException();out.put("desiredRate",n.toBigIntegerExact().toString(),false,"");}catch(ArithmeticException e){out.invalid("desiredRate","희망 단가를 원/월 정수로 확인해 주세요.");}
                else out.invalid("desiredRate","연간/범위/복수 금액은 자동 입력하지 않습니다. 희망 단가(원/월)를 확인해 주세요.");
            }
            String date=unique(fields,"FROM","availableDate",out);
            if(date!=null)try{String value;if(Set.of("즉시","바로").contains(date.strip()))value=today.toString();else {Matcher m=Pattern.compile("^([1-9][0-9]{3})\\s*[-./년]\\s*([0-9]{1,2})\\s*[-./월]\\s*([0-9]{1,2})\\s*일?$").matcher(date.strip());if(!m.matches())throw new DateTimeException("invalid");value=LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))).toString();}out.put("availableDate",value,true,value);}catch(DateTimeException e){out.invalid("availableDate","연도를 포함한 실제 투입 가능일을 확인해 주세요.");}
        }
        String skill=unique(fields,"SKILL","skills",out);
        if(skill!=null){LinkedHashMap<String,String> skills=new LinkedHashMap<>();for(String s:skill.split("[,/·|\\r\\n]")){String v=s.strip();if(!v.isEmpty())skills.putIfAbsent(v.toLowerCase(Locale.ROOT),v);}if(!skills.isEmpty()&&skills.size()<=999&&skills.values().stream().allMatch(s->s.codePointCount(0,s.length())<=50))out.put("skills",List.copyOf(skills.values()),false,"");else out.invalid("skills","기술은 각 50자 이내, 최대 999개로 확인해 주세요.");}
        else if(!fields.containsKey("SKILL")) {var skills=dictionary.codes().stream().filter(c->c.groupId().equals("SKILL")&&count(text,c.name())>0).map(Code::name).distinct().toList();if(!skills.isEmpty())out.put("skills",skills,true,String.join(", ",skills));}
        String address=unique(fields,"ADDR","homeAreaCode",out);if(address!=null)address(address,target,dictionary,out);
        jobs(text,dictionary,out);
        String vendor=unique(fields,"VNDR","companyId",out);boolean freelance=Pattern.compile("프리랜서|(?<![a-z0-9])freelancer(?![a-z0-9])",Pattern.CASE_INSENSITIVE).matcher(text).find();
        if(vendor!=null&&freelance)out.warnings.add("프리랜서와 외주 업체 소속 근거가 함께 있습니다. 구분과 업체를 직접 확인해 주세요.");
        else if(vendor!=null){out.put("employeeType","OS",true,"소속 업체 이름표");var matches=dictionary.vendors().stream().filter(v->vendorKey(v.name()).equals(vendorKey(vendor))).toList();if(matches.size()==1)out.put("companyId",matches.get(0).id(),true,matches.get(0).name());else out.invalid("companyId","사용 중인 협력사의 유일 일치를 찾지 못했습니다. 업체를 직접 선택해 주세요.");}
        else if(freelance)out.put("employeeType","FL",true,"프리랜서");
        var career=Pattern.compile("경력\\s*[0-9]{1,2}\\s*년(?:\\s*[0-9]{1,2}\\s*개월)?").matcher(text);return out.finish(career.find()?career.group():null);
    }
    private static void contacts(String text,Map<String,List<String>> fields,Output out) {
        List<String> phones=new ArrayList<>(fields.getOrDefault("TEL",List.of()));
        if(phones.isEmpty()){var m=Pattern.compile("(?:\\+82[- ]?|0)1[016789][- ]?[0-9]{3,4}[- ]?[0-9]{4}(?![0-9])").matcher(text);while(m.find())phones.add(m.group());}
        Set<String> normalized=new LinkedHashSet<>();boolean invalid=false;
        for(String p:phones){String n=p.replaceAll("[^0-9]","");if(p.strip().startsWith("+82"))n="0"+n.substring(2);if(n.matches("01[016789][0-9]{7,8}"))normalized.add(n.substring(0,3)+"-"+n.substring(3,n.length()-4)+"-"+n.substring(n.length()-4));else if(p.matches("[0-9][0-9-]{7,19}"))normalized.add(p);else invalid=true;}
        if(normalized.size()==1&&!invalid){String p=normalized.iterator().next();out.put("phone",p,!p.matches("01[016789]-[0-9]{3,4}-[0-9]{4}"),p);}else if(!phones.isEmpty())out.invalid("phone","전화번호가 여러 개이거나 불명확합니다. 직접 선택해 주세요.",normalized.size()>1&&!invalid?new ArrayList<>(normalized):List.of());
        List<String> emails=new ArrayList<>(fields.getOrDefault("MAIL",List.of()));if(emails.isEmpty()){var m=Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);while(m.find())emails.add(m.group());}
        Set<String> unique=new LinkedHashSet<>();boolean bad=false;for(String e:emails){String value=e.strip().toLowerCase(Locale.ROOT);if(value.length()<=100&&value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))unique.add(value);else bad=true;}
        if(unique.size()==1&&!bad)out.put("email",unique.iterator().next(),false,"");else if(!emails.isEmpty())out.invalid("email","이메일이 여러 개이거나 유효하지 않습니다. 직접 확인해 주세요.",unique.size()>1&&!bad?new ArrayList<>(unique):List.of());
    }
    private static String unique(Map<String,List<String>> fields,String label,String field,Output out) {
        var values=fields.getOrDefault(label,List.of()).stream().map(String::strip).distinct().toList();if(values.size()>1){out.invalid(field,"서로 다른 값이 여러 개입니다. 자동 선택하지 않습니다.");return null;}return values.isEmpty()?null:values.get(0);
    }
    private static void address(String raw,String target,Dictionary dictionary,Output out) {
        String text=normalize(raw);var areas=dictionary.areas();List<Area> roots=areas.stream().filter(a->a.level().equals("SIDO")&&(text.startsWith(normalize(a.sido()))||text.startsWith(normalize(a.fullSido())))).toList();
        if(roots.size()!=1){out.invalid("homeAreaCode","시·도를 유일하게 확인할 수 없습니다. 주소를 직접 선택해 주세요.");return;}
        Area selected=roots.get(0);String root=selected.code();var children=areas.stream().filter(a->root.equals(a.parentCode())&&a.level().equals("SGG")&&a.sgg()!=null&&text.contains(normalize(a.sgg()))).toList();
        if(!children.isEmpty()){int max=children.stream().mapToInt(a->normalize(a.sgg()).length()).max().orElse(0);var longest=children.stream().filter(a->normalize(a.sgg()).length()==max).toList();if(longest.size()==1)selected=longest.get(0);else out.warnings.add("하위 주소가 모호하여 확인된 상위 주소까지만 제안합니다.");}
        if(target.equals("DEVELOPER")){String parent=selected.code();var towns=areas.stream().filter(a->parent.equals(a.parentCode())&&a.level().equals("EMD")&&a.emd()!=null&&text.contains(normalize(a.emd()))).toList();if(towns.size()==1)selected=towns.get(0);else if(towns.size()>1)out.warnings.add("읍·면·동이 모호하여 상위 주소만 제안합니다.");}
        String display=String.join(" ",java.util.stream.Stream.of(selected.sido(),selected.sgg(),selected.emd()).filter(Objects::nonNull).filter(s->!s.isBlank()).toList());out.put("homeAreaCode",selected.code(),selected.level().equals("SIDO"),display);
    }
    private record Hit(int start,int end,String word,List<String> jobs) {}
    private static void jobs(String text,Dictionary dictionary,Output out) {
        Map<String,Code> jobs=new LinkedHashMap<>();dictionary.codes().stream().filter(c->c.groupId().equals("JOB")).forEach(c->jobs.put(c.code(),c));List<Hit> hits=new ArrayList<>();
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        for(Code c:dictionary.codes())if(c.groupId().equals("RSM_JOB_KW")&&c.addVal1()!=null){var codes=Arrays.stream(c.addVal1().split(",")).map(String::strip).distinct().filter(jobs::containsKey).toList();if(codes.isEmpty())continue;var m=wordPattern(c.name()).matcher(normalized);while(m.find())hits.add(new Hit(m.start(),m.end(),c.name(),codes));}
        hits.sort(Comparator.comparingInt((Hit h)->h.end()-h.start()).reversed().thenComparingInt(Hit::start));BitSet occupied=new BitSet(normalized.length());Map<String,Integer> counts=new HashMap<>();Map<String,Set<String>> words=new HashMap<>();
        for(Hit h:hits){if(occupied.get(h.start(),h.end()).isEmpty()){occupied.set(h.start(),h.end());for(String job:h.jobs()){counts.merge(job,1,Integer::sum);words.computeIfAbsent(job,k->new LinkedHashSet<>()).add(h.word());}}}
        var ranked=counts.keySet().stream().sorted(Comparator.comparingInt((String j)->counts.get(j)).reversed().thenComparingInt(j->jobs.get(j).sortOrder()).thenComparing(j->j)).limit(2).toList();
        for(int i=0;i<ranked.size();i++){String code=ranked.get(i),field=i==0?"jobCode":"subJobCode";out.put(field,code,true,String.join(", ",words.get(code)));Field evidence=out.fields.remove(out.fields.size()-1);out.fields.add(new Field(evidence.field(),evidence.valid(),evidence.confidence(),evidence.notice(),evidence.fragment(),List.copyOf(words.get(code)),List.of()));}
    }
    static Pattern wordPattern(String word){String w=Normalizer.normalize(word,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);return Pattern.compile((w.matches("^[a-z0-9].*")?"(?<![a-z0-9])":"")+Pattern.quote(w)+(w.matches(".*[a-z0-9]$")?"(?![a-z0-9])":""));}
    static int count(String text,String word){return (int)wordPattern(word).matcher(Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).results().count();}
    private static class Output {
        final String target;final Map<String,Object> values=new LinkedHashMap<>();final List<Field> fields=new ArrayList<>();final List<String> warnings=new ArrayList<>();
        Output(String target){this.target=target;}
        void put(String key,Object value,boolean inferred,String fragment){values.put(key,value);String safe=fragment.replaceAll("[0-9]{6}[- ]?[1-4][0-9]{6}","[삭제]");if(safe.length()>60)safe=safe.substring(0,60);fields.add(new Field(key,true,inferred?"INFERRED":"EXPLICIT",inferred?"⚠ 확인해 주세요":"✓ 이력서에서 가져옴",safe,List.of(),List.of()));}
        void invalid(String field,String message){invalid(field,message,List.of());}
        void invalid(String field,String message,List<Object> candidates){fields.add(new Field(field,false,"INFERRED",message,"",List.of(),List.copyOf(candidates)));}
        Extraction finish(String career){return new Extraction(target,Collections.unmodifiableMap(values),List.copyOf(fields),List.copyOf(warnings),career,values.isEmpty()?"가져올 칸을 찾지 못했습니다. 직접 적어 주세요.":"값 후보만 반환합니다. 확인 후 [등록]을 누르세요. 파일은 저장하지 않습니다.");}
    }
}
