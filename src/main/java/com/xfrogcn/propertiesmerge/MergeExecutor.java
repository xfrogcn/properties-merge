package com.xfrogcn.propertiesmerge;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MergeExecutor implements ApplicationRunner {

    private static Log log = LogFactory.getLog(MergeExecutor.class);

    @Autowired
    Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> files = args.getOptionValues("merge-files");
        if (files == null) {
            files = new ArrayList<>();
        }else{
            files = files.stream().collect(Collectors.toList());
        }
        String envFile = System.getenv("merge-files");
        if (Strings.isNotBlank(envFile)) {
            files.add(envFile);
        }

        if (files.isEmpty()) {
            log.info("no merge files found.");
            return;
        }

        files = files.stream().distinct().collect(Collectors.toList());
        log.info(String.format("found %d merge files", files.size()));

        HashMap<String, HashSet<String>> mergeMap = new HashMap<>();
        for(String file : files) {
            File f = new File(file);
            if(!f.exists()){
                log.warn("file not exists: " + file);
                continue;
            }

            FileInputStream inputStream = new FileInputStream(f);
            Properties mergeProperties = new Properties();
            mergeProperties.load(inputStream);

            Enumeration names = mergeProperties.propertyNames();
            while (names.hasMoreElements()){
                String name = (String) names.nextElement();
                if(Strings.isBlank(name)){
                    continue;
                }

                String targets = mergeProperties.getProperty(name);
                if(Strings.isBlank(targets)){
                    continue;
                }

                mergeMap.putIfAbsent(name, new HashSet<>());
                HashSet<String> mergedList = mergeMap.get(name);
                String[] targetList = targets.split(",");
                innerMerge(name, targetList, mergedList);
            }

            inputStream.close();
        }

    }

    private void  innerMerge(String source, String[] targets, HashSet<String> mergedList){
        Properties sourceProperties = new Properties();
        File sourceFile = new File(source);
        FileInputStream sourceInputStream = null;
        if(sourceFile.exists() && sourceFile.isFile()) {
            try {
                sourceInputStream = new FileInputStream(sourceFile);

                sourceProperties.load(sourceInputStream);

            } catch (FileNotFoundException e) {
                // nothing
            } catch (IOException e) {

            }
        }

        if(sourceFile.exists() && !sourceFile.isFile()){
            log.warn("source properties path is not file!" + " " + source);
            return;
        }

        for(String target : targets) {
            if (mergedList.contains(target)) {
                continue;
            }
            File targetFile = new File(target);

            if (!targetFile.exists()) {
                continue;
            }

            try {
                FileInputStream targetStream = new FileInputStream(targetFile);
                Properties targetProperties = new Properties();
                targetProperties.load(targetStream);

                mergeProperties(sourceProperties, targetProperties);

            } catch (FileNotFoundException e) {
                log.error("file not found: ", e);
            } catch (IOException e) {
                log.error("load properties error: ", e);
            }

            mergedList.add(target);
        }

        if(sourceInputStream!=null){
            try {
                sourceInputStream.close();
            } catch (IOException e) {

            }
        }

        saveProperties(sourceProperties, source);

        log.info("properties merged: " + source);
    }

    private void mergeProperties(Properties source , Properties target){
        if( source == null || target == null){
            return;
        }

        Enumeration names = target.propertyNames();
        while (names.hasMoreElements()){
            String name = (String) names.nextElement();
            String value = target.getProperty(name, null);
            if( value!= null){
                source.setProperty(name, value);
            }
        }
    }

    private void saveProperties(Properties source, String file) {
        File f = new File(file);
        if (f.exists() && f.isFile()) {
            f.delete();
        }
        try (FileOutputStream outputStream = new FileOutputStream(f)) {
            source.store(outputStream, "merged");
        } catch (Exception e) {
            log.error("save merged properties file error", e);
        }
    }
}
