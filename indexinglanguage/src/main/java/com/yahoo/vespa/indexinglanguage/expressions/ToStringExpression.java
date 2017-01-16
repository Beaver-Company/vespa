// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ToStringExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setValue(new StringFieldValue(String.valueOf(ctx.getValue())));
    }

    @Override
    protected void doVerify(VerificationContext ctx) {
        ctx.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "to_string";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToStringExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
