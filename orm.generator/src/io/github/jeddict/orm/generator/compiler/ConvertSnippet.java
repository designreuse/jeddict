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
package io.github.jeddict.orm.generator.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import static io.github.jeddict.jcode.jpa.JPAConstants.CONVERT;
import static io.github.jeddict.jcode.jpa.JPAConstants.CONVERT_FQN;
import static io.github.jeddict.settings.code.CodePanel.isGenerateDefaultValue;
import io.github.jeddict.jpa.spec.Convert;
import io.github.jeddict.orm.generator.util.ClassHelper;
import static io.github.jeddict.orm.generator.util.ORMConverterUtil.CLOSE_PARANTHESES;
import static io.github.jeddict.orm.generator.util.ORMConverterUtil.COMMA;
import static io.github.jeddict.orm.generator.util.ORMConverterUtil.QUOTE;

public class ConvertSnippet implements Snippet {

    private final ClassHelper converterClass;
    private final boolean disableConversion;
    private final String attributeName;

    public ConvertSnippet(Convert convert) {
        converterClass = StringUtils.isNotBlank(convert.getConverter()) ? new ClassHelper(convert.getConverter()) : null;
        disableConversion = convert.isDisableConversion();
        attributeName = convert.getAttributeName();
    }

    @Override
    public String getSnippet() throws InvalidDataException {
        StringBuilder builder = new StringBuilder();
        builder.append("@").append(CONVERT).append("(");

//        if (converterClass != null && StringUtils.isBlank(attributeName) && !disableConversion && !isGenerateDefaultValue()) {
//            return builder.append(converterClass.getClassNameWithClassSuffix()).append(")").toString();
//        }
//        
//        if (converterClass == null && StringUtils.isBlank(attributeName) && disableConversion && !isGenerateDefaultValue()) {
//            return builder.append(disableConversion).append(")").toString();
//        }
        
        if (converterClass != null) {
            builder.append("converter=");
            builder.append(converterClass.getClassNameWithClassSuffix());
            builder.append(COMMA);
        }

        if (isGenerateDefaultValue() || disableConversion) {
            builder.append("disableConversion=");
            builder.append(disableConversion);
            builder.append(COMMA);
        }

        if (!StringUtils.isBlank(attributeName)) {
            builder.append("attributeName=\"");
            builder.append(attributeName);
            builder.append(QUOTE);
            builder.append(COMMA);
        }
        return builder.substring(0, builder.length() - 1) + CLOSE_PARANTHESES;
    }

    @Override
    public Collection<String> getImportSnippets() throws InvalidDataException {
        List<String> importSnippets = new ArrayList<>();
        importSnippets.add(CONVERT_FQN);
        if (converterClass != null) {
            importSnippets.add(converterClass.getFQClassName());
        }
        return importSnippets;
    }
}
