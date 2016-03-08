/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.dmr;
import dap4.core.util.DapSort;

public interface DapFactory
{
    DapAttribute newAttribute(String name, DapType basetype);
    DapAttributeSet newAttributeSet(String name);
    DapOtherXML newOtherXML(String name);
    DapDimension newDimension(String name, long size);
    DapMap newMap(DapVariable target);
    DapAtomicVariable newAtomicVariable(String name, DapType t);
    DapVariable newVariable(String name, DapType t);
    DapGroup newGroup(String name);
    DapDataset newDataset(String name);
    DapEnumeration newEnumeration(String name, DapType basetype);
    DapEnumConst newEnumConst(String name, long value);
    DapStructure newStructure(String name);
    DapSequence newSequence(String name);
}
