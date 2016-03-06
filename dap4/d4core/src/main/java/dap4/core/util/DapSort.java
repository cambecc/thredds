/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.util;

/**
 * Define an enumeration for all the DapNode subclasses to
 * avoid use of instanceof().  Note that this mixes
 * DAP2 and DAP4 for eventual joint support.
 */

import dap4.core.dmr.*;

/**
 * Define the kinds of AST objects to avoid having to do instanceof.
 * The name field is for debugging.
 */
public enum DapSort
{
    ATOMICTYPE("AtomicType",DapType.class,null),
    ATTRIBUTESET("AttributeSet", DapAttributeSet.class, null),
    OTHERXML("OtherXML", DapOtherXML.class, null),
    ATTRIBUTE("Attribute", DapAttribute.class, ATTRIBUTESET, OTHERXML),
    XML("XML", DapXML.class, null),
    DIMENSION("Dimension", DapDimension.class, null),
    MAP("Map", DapMap.class, null),
    ATOMICVARIABLE("Variable", DapVariable.class, null),
    DATASET("Dataset", DapDataset.class, null),
    GROUP("Group", DapGroup.class, DATASET),
    ENUMERATION("Enumeration", DapEnumeration.class, null),
    ENUMCONST("EnumConst", DapEnumConst.class, null),
    SEQUENCE("Sequence", DapSequence.class, null),
    STRUCTURE("Structure", DapStructure.class, SEQUENCE),;

    private final String name;
    private final Class classfor;
    private final DapSort[] subsorts;

    DapSort(String name, Class classfor, DapSort... subsorts)
    {
        this.name = name;
        this.classfor = classfor;
        this.subsorts = subsorts;
    }

    public final String getName()
    {
        return this.name;
    }

    public final Class getClassFor()
    {
        return this.classfor;
    }

    public boolean isa(DapSort supersort)
    {
        if(supersort == this)
            return true;
        for(DapSort sub : supersort.subsorts) {
            if(sub == this) return true;
        }
        return false;
    }

    /*
static public boolean isa(DapSort sort, DapNode node)
    {
        return sort == null
                || sort.getClassFor().isInstance(node);
    }
*/
};

