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
package io.github.jeddict.orm.generator.compiler.constraints;

import io.github.jeddict.orm.generator.compiler.InvalidDataException;
import static java.lang.Boolean.FALSE;
import org.apache.commons.lang3.StringUtils;
import io.github.jeddict.bv.constraints.DecimalMin;
import io.github.jeddict.settings.code.CodePanel;
import io.github.jeddict.orm.generator.util.ORMConverterUtil;

/**
 *
 * @author Gaurav Gupta
 */
public class DecimalMinSnippet extends ConstraintSnippet<DecimalMin> {

    public DecimalMinSnippet(DecimalMin decimalMin) {
        super(decimalMin);
    }

    @Override
    protected String getAPI() {
        return "DecimalMin";
    }

    @Override
    public String getSnippet() throws InvalidDataException {
        if (constraint.getMessage() == null 
                && StringUtils.isBlank(constraint.getValue())
                && !FALSE.equals(constraint.getInclusive())) {
            return "@" + getAPI();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("@").append(getAPI()).append(ORMConverterUtil.OPEN_PARANTHESES);

        if (!StringUtils.isBlank(constraint.getValue())) {
            builder.append("value=\"");
            builder.append(constraint.getValue());
            builder.append(ORMConverterUtil.QUOTE);
            builder.append(ORMConverterUtil.COMMA);
        }
        
        if (CodePanel.isGenerateDefaultValue()) {
            boolean inclusive = !FALSE.equals(constraint.getInclusive());
            builder.append("inclusive=");
            builder.append(inclusive);
            builder.append(ORMConverterUtil.COMMA);
        } else if (FALSE.equals(constraint.getInclusive())) {
            builder.append("inclusive=");
            builder.append(constraint.getInclusive());
            builder.append(ORMConverterUtil.COMMA);
        }

         if (constraint.getMessage() != null) {
            builder.append("message=\"");
            builder.append(constraint.getMessage());
            builder.append(ORMConverterUtil.QUOTE);
            builder.append(ORMConverterUtil.COMMA);
        }

        return builder.substring(0, builder.length() - 1) + ORMConverterUtil.CLOSE_PARANTHESES;
    }

}
