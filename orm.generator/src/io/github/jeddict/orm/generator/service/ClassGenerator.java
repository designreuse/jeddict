/**
 * Copyright 2013-2018 the original author or authors from the Jeddict project (https://jeddict.github.io/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.github.jeddict.orm.generator.service;

import static java.lang.Boolean.TRUE;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import org.apache.commons.lang3.StringUtils;
import io.github.jeddict.infra.JavaEEVersion;
import static io.github.jeddict.infra.JavaEEVersion.JAVA_EE_8;
import io.github.jeddict.bv.constraints.Constraint;
import io.github.jeddict.settings.code.CodePanel;
import io.github.jeddict.jpa.spec.DefaultClass;
import io.github.jeddict.jpa.spec.EntityMappings;
import io.github.jeddict.jpa.spec.extend.AnnotationLocation;
import io.github.jeddict.jpa.spec.extend.Attribute;
import io.github.jeddict.jpa.spec.extend.AttributeSnippet;
import io.github.jeddict.jpa.spec.extend.AttributeSnippetLocationType;
import io.github.jeddict.jpa.spec.extend.ClassMembers;
import io.github.jeddict.jpa.spec.extend.ClassSnippet;
import io.github.jeddict.jpa.spec.extend.ClassSnippetLocationType;
import io.github.jeddict.jpa.spec.extend.Constructor;
import io.github.jeddict.jpa.spec.extend.IAttributes;
import io.github.jeddict.jpa.spec.extend.JavaClass;
import io.github.jeddict.jpa.spec.extend.ReferenceClass;
import io.github.jeddict.jpa.spec.extend.Snippet;
import io.github.jeddict.jpa.spec.extend.SnippetLocation;
import io.github.jeddict.jpa.spec.extend.annotation.Annotation;
import io.github.jeddict.jsonb.generator.compiler.DateFormatSnippet;
import io.github.jeddict.jsonb.generator.compiler.NillableSnippet;
import io.github.jeddict.jsonb.generator.compiler.NumberFormatSnippet;
import io.github.jeddict.jsonb.generator.compiler.PropertyOrderSnippet;
import io.github.jeddict.jsonb.generator.compiler.PropertySnippet;
import io.github.jeddict.jsonb.generator.compiler.TransientSnippet;
import io.github.jeddict.jsonb.generator.compiler.TypeAdapterSnippet;
import io.github.jeddict.jsonb.generator.compiler.TypeDeserializerSnippet;
import io.github.jeddict.jsonb.generator.compiler.TypeSerializerSnippet;
import io.github.jeddict.jsonb.generator.compiler.VisibilitySnippet;
import io.github.jeddict.orm.generator.compiler.AnnotationSnippet;
import io.github.jeddict.orm.generator.compiler.ConstructorSnippet;
import io.github.jeddict.orm.generator.compiler.EqualsMethodSnippet;
import io.github.jeddict.orm.generator.compiler.HashcodeMethodSnippet;
import io.github.jeddict.orm.generator.compiler.ToStringMethodSnippet;
import io.github.jeddict.orm.generator.compiler.def.VariableDefSnippet;
import io.github.jeddict.orm.generator.compiler.constraints.ConstraintSnippet;
import io.github.jeddict.orm.generator.compiler.constraints.ConstraintSnippetFactory;
import io.github.jeddict.orm.generator.compiler.def.ClassDefSnippet;
import io.github.jeddict.orm.generator.util.ClassHelper;
import io.github.jeddict.orm.generator.util.ORMConvLogger;

public abstract class ClassGenerator<T extends ClassDefSnippet> {

    protected static final Logger logger = ORMConvLogger.getLogger(ClassGenerator.class);

    protected String rootPackageName;
    protected String packageName;
    protected T classDef;
    protected Map<String, VariableDefSnippet> variables = new LinkedHashMap<>();
    protected JavaEEVersion javaEEVersion;
    protected boolean repeatable;

    public ClassGenerator(T classDef, JavaEEVersion javaEEVersion) {
        this.classDef = classDef;
        this.javaEEVersion = javaEEVersion;
        this.repeatable = javaEEVersion.getVersion()<JAVA_EE_8.getVersion();
    }

    public abstract T getClassDef();

    protected T initClassDef(String packageName, JavaClass javaClass) {
        ClassHelper classHelper = new ClassHelper(javaClass.getClazz());
        classHelper.setPackageName(packageName);
        classDef.setClassName(classHelper.getFQClassName());
        classDef.setAbstractClass(javaClass.getAbstract());

        if (!(javaClass instanceof DefaultClass)) { // custom interface support skiped for IdClass/EmbeddedId
            Set<ReferenceClass> interfaces = new LinkedHashSet<>(javaClass.getRootElement().getInterfaces());
            interfaces.addAll(javaClass.getInterfaces());
            classDef.setInterfaces(interfaces.stream().filter(ReferenceClass::isEnable).map(ReferenceClass::getName).collect(toList()));
        }
        
        classDef.setJSONBSnippets(getJSONBClassSnippet(javaClass));
        classDef.setAnnotation(getAnnotationSnippet(javaClass.getAnnotation()));
        classDef.getAnnotation().putAll(getAnnotationSnippet(javaClass.getRuntimeAnnotation()));
        
        List<ClassSnippet> snippets = new ArrayList<>(javaClass.getRootElement().getSnippets());
        snippets.addAll(javaClass.getSnippets());
        snippets.addAll(javaClass.getRuntimeSnippets());
        classDef.setCustomSnippet(getCustomSnippet(snippets));

        classDef.setVariableDefs(new ArrayList<>(variables.values()));

        classDef.setConstructors(getConstructorSnippets(javaClass));
        classDef.setHashcodeMethod(getHashcodeMethodSnippet(javaClass, getClassMembers(javaClass, javaClass.getHashCodeMethod())));
        classDef.setEqualsMethod(getEqualsMethodSnippet(javaClass, getClassMembers(javaClass, javaClass.getEqualsMethod())));
        classDef.setToStringMethod(getToStringMethodSnippet(javaClass, getClassMembers(javaClass, javaClass.getToStringMethod())));

        if (javaClass.getSuperclass() != null) {
            classDef.setSuperClassName(javaClass.getSuperclass().getFQN());
        } else if (javaClass.getSuperclassRef() != null) {
            classDef.setSuperClassName(javaClass.getSuperclassRef().getName());
        }
        return classDef;
    }

    private ClassMembers getClassMembers(JavaClass javaClass, ClassMembers classMembers) {
        if (javaClass instanceof DefaultClass) {
            classMembers = new ClassMembers();
            for (VariableDefSnippet variableDefSnippet : variables.values()) {
                if (variableDefSnippet.getAttribute() != null) {
                    classMembers.addAttribute(variableDefSnippet.getAttribute());
                }
            }
        }
        return classMembers;
    }
    protected <T extends AnnotationLocation> Map<T, List<AnnotationSnippet>> getAnnotationSnippet(List<? extends Annotation<T>> annotations) {
        Map<T, List<AnnotationSnippet>> snippetsMap = new HashMap<>();
        for (Annotation<T> annotation : annotations) {
            if (annotation.isEnable()) {
                if (snippetsMap.get(annotation.getLocationType()) == null) {
                    snippetsMap.put(annotation.getLocationType(), new ArrayList<>());
                }
                AnnotationSnippet snippet = new AnnotationSnippet();
                snippet.setName(annotation.getName());
                snippetsMap.get(annotation.getLocationType()).add(snippet);
            }
        }
        return snippetsMap;
    }

    protected <T extends SnippetLocation> Map<T, List<String>> getCustomSnippet(List<? extends Snippet<T>> snippets) {
        Map<T, List<String>> snippetsMap = new HashMap<>();
        for (Snippet<T> snippet : snippets) {
            if (snippet.isEnable()) {
                if (snippetsMap.get(snippet.getLocationType()) == null) {
                    snippetsMap.put(snippet.getLocationType(), new ArrayList<>());
                }
                String value = snippet.getValue();
                if(snippet.getLocationType() == ClassSnippetLocationType.IMPORT ||
                        snippet.getLocationType() == AttributeSnippetLocationType.IMPORT){
                    if(!value.startsWith("import")){
                        value = "import " + value;
                    }
                    if(!value.endsWith(";")){
                        value = value + ";";
                    }
                }
                snippetsMap.get(snippet.getLocationType()).add(value);
            }
        }
        return snippetsMap;
    }

    protected HashcodeMethodSnippet getHashcodeMethodSnippet(JavaClass javaClass, ClassMembers classMembers) {
        if (classMembers.getAttributes().isEmpty()
                && StringUtils.isBlank(classMembers.getPreCode())
                && StringUtils.isBlank(classMembers.getPostCode())) {
            return null;
        }
        return new HashcodeMethodSnippet(javaClass.getClazz(), classMembers);
    }

    protected EqualsMethodSnippet getEqualsMethodSnippet(JavaClass javaClass, ClassMembers classMembers) {
        if (classMembers.getAttributes().isEmpty()
                && StringUtils.isBlank(classMembers.getPreCode())
                && StringUtils.isBlank(classMembers.getPostCode())) {
            return null;
        }
        return new EqualsMethodSnippet(javaClass.getClazz(), classMembers);
    }

    protected ToStringMethodSnippet getToStringMethodSnippet(JavaClass javaClass, ClassMembers classMembers) {
        if (classMembers.getAttributes().isEmpty()) {
            return null;
        }
        ToStringMethodSnippet snippet = new ToStringMethodSnippet(javaClass.getClazz());
        snippet.setAttributes(classMembers.getAttributeNames());
        return snippet;
    }

    protected List<ConstructorSnippet> getConstructorSnippets(JavaClass javaClass) {
        List<ConstructorSnippet> constructorSnippets = new ArrayList<>();       
        List<Constructor> constructors = javaClass.getConstructors();
        if (javaClass instanceof DefaultClass && constructors.isEmpty()) { //for EmbeddedId and IdClass
            constructors.add(Constructor.getNoArgsInstance());
            Constructor constructor = new Constructor();
            constructor.setAttributes(getClassMembers(javaClass, null).getAttributes());
            constructors.add(constructor);
        }

        constructors.stream().filter(Constructor::isEnable).map(constructor -> {
            String className = javaClass.getClazz();
            List<VariableDefSnippet> parentVariableSnippets = constructor.getAttributes()
                    .stream()
                    .filter(attr -> attr.getJavaClass() != javaClass)
                    .map(attr -> getVariableDef(attr))
                    .collect(toList());
            List<VariableDefSnippet> localVariableSnippets = constructor.getAttributes()
                    .stream()
                    .filter(attr -> attr.getJavaClass() == javaClass)
                    .map(attr -> getVariableDef(attr))
                    .collect(toList());
            ConstructorSnippet snippet = new ConstructorSnippet(className, constructor, parentVariableSnippets, localVariableSnippets);
            return snippet;
        }).forEach(constructorSnippets::add);
        return constructorSnippets;
    }

    protected List<ConstraintSnippet> getConstraintSnippet(Set<Constraint> constraints) {
        List<ConstraintSnippet> snippets = new ArrayList<>();
        for (Constraint constraint : constraints) {
            if (!constraint.getSelected() || constraint.isEmpty()) {
                continue;
            }
            ConstraintSnippet snippet = ConstraintSnippetFactory.getInstance(constraint);
            if (snippet != null) {
                snippets.add(snippet);
            }
        }
        return snippets;
    }
    
    protected List<io.github.jeddict.orm.generator.compiler.Snippet> getJSONBAttributeSnippet(Attribute attribute) {
        List<io.github.jeddict.orm.generator.compiler.Snippet> snippets = new ArrayList<>();
        if (attribute.getJsonbTransient()) {
            snippets.add(new TransientSnippet());
        } else {
            if (StringUtils.isNotBlank(attribute.getJsonbProperty()) || attribute.getJsonbNillable()) {
                snippets.add(new PropertySnippet(attribute.getJsonbProperty(), attribute.getJsonbNillable()));
            }
            if (attribute.getJsonbDateFormat() != null
                    && (StringUtils.isNotBlank(attribute.getJsonbDateFormat().getValue())
                    || StringUtils.isNotBlank(attribute.getJsonbDateFormat().getLocale()))) {
                snippets.add(new DateFormatSnippet(attribute.getJsonbDateFormat()));
            }
            if (attribute.getJsonbNumberFormat() != null
                    && (StringUtils.isNotBlank(attribute.getJsonbNumberFormat().getValue())
                    || StringUtils.isNotBlank(attribute.getJsonbNumberFormat().getLocale()))) {
                snippets.add(new NumberFormatSnippet(attribute.getJsonbNumberFormat()));
            }
            if (attribute.getJsonbTypeAdapter() != null
                    && attribute.getJsonbTypeAdapter().isEnable()
                    && !StringUtils.isBlank(attribute.getJsonbTypeAdapter().getName())) {
                snippets.add(new TypeAdapterSnippet(attribute.getJsonbTypeAdapter()));
            }
            if (attribute.getJsonbTypeDeserializer() != null
                    && attribute.getJsonbTypeDeserializer().isEnable()
                    && !StringUtils.isBlank(attribute.getJsonbTypeDeserializer().getName())) {
                snippets.add(new TypeDeserializerSnippet(attribute.getJsonbTypeDeserializer()));
            }
            if (attribute.getJsonbTypeSerializer() != null
                    && attribute.getJsonbTypeSerializer().isEnable()
                    && !StringUtils.isBlank(attribute.getJsonbTypeSerializer().getName())) {
                snippets.add(new TypeSerializerSnippet(attribute.getJsonbTypeSerializer()));
            }
        }
        return snippets;
    }

    protected List<io.github.jeddict.orm.generator.compiler.Snippet> getJSONBClassSnippet(JavaClass<IAttributes> javaClass) {
        List<io.github.jeddict.orm.generator.compiler.Snippet> snippets = new ArrayList<>();
        if(javaClass.getJsonbNillable()){
            snippets.add(new NillableSnippet(javaClass.getJsonbNillable()));
        }
        if (!javaClass.getJsonbPropertyOrder().isEmpty()) {
            snippets.add(new PropertyOrderSnippet(javaClass.getJsonbPropertyOrder()
                    .stream()
                    .map(Attribute::getName)
                    .collect(toList())
                )
            );
        }
        if (javaClass.getJsonbDateFormat() != null && !javaClass.getJsonbDateFormat().isEmpty()) {
            snippets.add(new DateFormatSnippet(javaClass.getJsonbDateFormat()));
        }
        if (javaClass.getJsonbNumberFormat() != null && !javaClass.getJsonbNumberFormat().isEmpty()) {
            snippets.add(new NumberFormatSnippet(javaClass.getJsonbNumberFormat()));
        }
        if(javaClass.getJsonbTypeAdapter()!=null 
                && javaClass.getJsonbTypeAdapter().isEnable() 
                && !StringUtils.isBlank(javaClass.getJsonbTypeAdapter().getName())){
            snippets.add(new TypeAdapterSnippet(javaClass.getJsonbTypeAdapter()));
        }
        if(javaClass.getJsonbTypeDeserializer()!=null 
                && javaClass.getJsonbTypeDeserializer().isEnable() 
                && !StringUtils.isBlank(javaClass.getJsonbTypeDeserializer().getName())){
            snippets.add(new TypeDeserializerSnippet(javaClass.getJsonbTypeDeserializer()));
        }
        if(javaClass.getJsonbTypeSerializer()!=null 
                && javaClass.getJsonbTypeSerializer().isEnable() 
                && !StringUtils.isBlank(javaClass.getJsonbTypeSerializer().getName())){
            snippets.add(new TypeSerializerSnippet(javaClass.getJsonbTypeSerializer()));
        }
        if(javaClass.getJsonbVisibility()!=null 
                && javaClass.getJsonbVisibility().isEnable() 
                && !StringUtils.isBlank(javaClass.getJsonbVisibility().getName())){
            snippets.add(new VisibilitySnippet(javaClass.getJsonbVisibility()));
        }
        return snippets;
    }

    protected List<io.github.jeddict.orm.generator.compiler.Snippet> getJSONBCPackageSnippet(EntityMappings entityMappings) {
        List<io.github.jeddict.orm.generator.compiler.Snippet> snippets = new ArrayList<>();
        if(entityMappings.getJsonbNillable()){
            snippets.add(new NillableSnippet(entityMappings.getJsonbNillable()));
        }
        if (entityMappings.getJsonbDateFormat() != null
                && (StringUtils.isNotBlank(entityMappings.getJsonbDateFormat().getValue())
                || StringUtils.isNotBlank(entityMappings.getJsonbDateFormat().getLocale()))) {
            snippets.add(new DateFormatSnippet(entityMappings.getJsonbDateFormat()));
        }
        if (entityMappings.getJsonbNumberFormat() != null
                && (StringUtils.isNotBlank(entityMappings.getJsonbNumberFormat().getValue())
                || StringUtils.isNotBlank(entityMappings.getJsonbNumberFormat().getLocale()))) {
            snippets.add(new NumberFormatSnippet(entityMappings.getJsonbNumberFormat()));
        }
        if (entityMappings.getJsonbVisibility() != null 
                && entityMappings.getJsonbVisibility().isEnable() 
                && !StringUtils.isBlank(entityMappings.getJsonbVisibility().getName())){
            snippets.add(new VisibilitySnippet(entityMappings.getJsonbVisibility()));
        }
        return snippets;
    }

    protected VariableDefSnippet getVariableDef(Attribute attr) {
        VariableDefSnippet variableDef = variables.get(attr.getName());
        if (variableDef == null) {
            variableDef = new VariableDefSnippet(attr);
            variableDef.setAccessModifier(attr.getAccessModifier());
            variableDef.setName(attr.getName());
            variableDef.setDefaultValue(attr.getDefaultValue());
            variableDef.setDescription(attr.getDescription());
            if (CodePanel.isJavaSESupportEnable()) {
                variableDef.setPropertyChangeSupport(TRUE.equals(attr.getPropertyChangeSupport()));
                variableDef.setVetoableChangeSupport(TRUE.equals(attr.getVetoableChangeSupport()));
                if (TRUE.equals(attr.getPropertyChangeSupport())) {
                    classDef.setPropertyChangeSupport(true);
                }
                if (TRUE.equals(attr.getVetoableChangeSupport())) {
                    classDef.setVetoableChangeSupport(true);
                }
            }

            variableDef.setAttributeConstraints(getConstraintSnippet(attr.getAttributeConstraints()));
            variableDef.setKeyConstraints(getConstraintSnippet(attr.getKeyConstraints()));
            variableDef.setValueConstraints(getConstraintSnippet(attr.getValueConstraints()));
            variableDef.setJSONBSnippets(getJSONBAttributeSnippet(attr));

            List<AttributeSnippet> snippets = new ArrayList<>();//todo global attribute snippet at class level ; javaClass.getAttrSnippets());
            snippets.addAll(attr.getSnippets());
            snippets.addAll(attr.getRuntimeSnippets());
            variableDef.setCustomSnippet(getCustomSnippet(snippets));
            variableDef.setAnnotation(getAnnotationSnippet(attr.getAnnotation()));
            variableDef.getAnnotation().putAll(getAnnotationSnippet(attr.getRuntimeAnnotation()));
            
            variableDef.setJaxbVariableType(attr.getJaxbVariableType());
            variableDef.setJaxbWrapperMetadata(attr.getJaxbWrapperMetadata());
            variableDef.setJaxbMetadata(attr.getJaxbMetadata());
            variables.put(attr.getName(), variableDef);
        }
        return variableDef;
    }

}
