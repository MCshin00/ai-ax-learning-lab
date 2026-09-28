package lab.harness;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;

/** 전용 빌드 폴더의 새 JUnit 결과로 기능 실패와 실행 불가를 구별합니다. */
public final class GradleCheck implements HookHandler.Check {
    private final Path project;
    public GradleCheck(Path project) { this.project=project; }
    @Override public HookHandler.Result run() throws Exception {
        var config=HarnessMain.JSON.readTree(project.resolve("harness.json").toFile());
        String task=config.path("verificationTask").asText("acceptance");
        if (!task.matches("[A-Za-z][A-Za-z0-9]*")) throw new IllegalArgumentException("Gradle 검사 작업 이름을 확인하세요.");
        int seconds=config.path("timeoutSeconds").asInt(120);
        if (seconds < 1 || seconds > 120) throw new IllegalArgumentException("검사 제한은 1~120초입니다.");
        Path run=project.resolve(".local/harness/checks/"+UUID.randomUUID());
        Path build=run.resolve("build"), temp=run.resolve("tmp");
        Files.createDirectories(temp);
        boolean windows=System.getProperty("os.name").startsWith("Windows");
        var command=new ArrayList<>(windows?List.of("cmd","/d","/c","gradlew.bat"):List.of("sh","gradlew"));
        command.addAll(List.of(task,"--no-daemon","--console=plain","--rerun-tasks","-PcourseBuildDir="+build));
        var builder=new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true)
            .redirectOutput(run.resolve("gradle.log").toFile());
        var inherited=new HashMap<>(builder.environment());
        builder.environment().clear();
        for(String name:List.of("PATH","Path","SystemRoot","WINDIR","COMSPEC","JAVA_HOME","HOME","USERPROFILE","GRADLE_USER_HOME"))
            if(inherited.containsKey(name)) builder.environment().put(name,inherited.get(name));
        builder.environment().put("TEMP",temp.toString()); builder.environment().put("TMP",temp.toString());
        builder.environment().put("JAVA_TOOL_OPTIONS","-Djava.io.tmpdir=\""+temp+"\"");
        Process process=builder.start();
        if(!process.waitFor(seconds,TimeUnit.SECONDS)) {
            process.descendants().forEach(p->p.destroyForcibly()); process.destroyForcibly();
            return new HookHandler.Result("UNAVAILABLE","검사 시간 초과. "+project.relativize(run.resolve("gradle.log")));
        }
        Path reports=build.resolve(config.path("reportsDirectory").asText("test-results/acceptance")).normalize();
        if(!reports.startsWith(build)) throw new IllegalArgumentException("JUnit 결과 위치를 확인하세요.");
        return classify(reports,process.exitValue(),project.relativize(run.resolve("gradle.log")).toString(),project.relativize(reports).toString());
    }
    static HookHandler.Result classify(Path reports,int exit,String log) throws Exception {
        return classify(reports,exit,log,reports.getFileName().toString());
    }
    static HookHandler.Result classify(Path reports,int exit,String log,String resultLocation) throws Exception {
        int count=0,failed=0,skipped=0;
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var failures=new ArrayList<String>();
        if(Files.isDirectory(reports)) try(var files=Files.list(reports)) {
            for(Path file:files.filter(p->p.getFileName().toString().endsWith(".xml")).toList()) {
                var doc=factory.newDocumentBuilder().parse(file.toFile());
                var suite=doc.getDocumentElement();
                count+=Integer.parseInt(suite.getAttribute("tests"));
                failed+=Integer.parseInt(suite.getAttribute("failures"))+Integer.parseInt(suite.getAttribute("errors"));
                if(suite.hasAttribute("skipped")) skipped+=Integer.parseInt(suite.getAttribute("skipped"));
                var cases=suite.getElementsByTagName("testcase");
                for(int i=0;i<cases.getLength();i++) {
                    var item=(org.w3c.dom.Element)cases.item(i);
                    if(item.getElementsByTagName("failure").getLength()+item.getElementsByTagName("error").getLength()>0)
                        failures.add(item.getAttribute("classname")+"."+item.getAttribute("name"));
                }
            }
        }
        if(failed>0) return new HookHandler.Result("FAILED","실패 검사: "+String.join(", ",failures)+". 기대값과 실제값은 "+resultLocation+" 의 JUnit XML에서, 실행 과정은 "+log+" 에서 확인하세요.");
        if(exit==0 && count>skipped && skipped==0) return new HookHandler.Result("PASSED","선택한 검사가 통과했습니다.");
        return new HookHandler.Result("UNAVAILABLE","검사 실행을 완료하지 못했습니다. "+log+" 에서 빌드·의존성·검사 선택을 확인하세요.");
    }
}
