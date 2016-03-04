/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.dmr;

public class DapEnumConst extends DapNode
{
    //////////////////////////////////////////////////
    // Instance Variables

    protected Long value = null;

    //////////////////////////////////////////////////
    // Constructors

    public DapEnumConst(String name, Long value, DapEnumeration parent)
    {
        super(name);
        this.value = value;
        setParent(parent);
    }

    ///////////////////////////////////////////////////
    // Accessors

    public DapType getParent()
    {
        return (DapEnumeration) getParent();
    }

    public Long getValue()
    {
        return value;
    }

    /**
     * Convenience
     *
     * @return value cast as int
     */
    public int getIntValue()
    {
        return (int)(long)value;
    }

}
