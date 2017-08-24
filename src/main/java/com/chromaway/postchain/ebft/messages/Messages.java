package com.chromaway.postchain.ebft.messages;

import java.util.Vector;
import org.asnlab.asndt.runtime.type.Buffer;
import org.asnlab.asndt.runtime.type.AsnType;
import org.asnlab.asndt.runtime.type.AsnModule;
import org.asnlab.asndt.runtime.conv.*;

public class Messages extends AsnModule {
    public final static Messages instance = new Messages();

    private Messages() {
        super(Messages.class);
    }

    public static AsnType type(int id) {
        return instance.getType(id);
    }

    public static Object value(int valueId, AsnConverter converter) {
        return instance.getValue(valueId, converter);
    }

    public static Object object(int objectId, AsnConverter converter) {
        return instance.getObject(objectId, converter);
    }

    public static Vector objectSet(int objectSetId, AsnConverter converter) {
        return instance.getObjectSet(objectSetId, converter);
    }


}
