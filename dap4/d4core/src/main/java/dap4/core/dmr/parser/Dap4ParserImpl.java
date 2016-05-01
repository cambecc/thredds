/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.dmr.parser;

import dap4.core.dmr.*;
import dap4.core.util.DapException;
import dap4.core.util.DapSort;
import dap4.core.util.DapUtil;
import dap4.core.util.Escape;
import org.xml.sax.SAXException;

import java.math.BigInteger;
import java.util.*;

/**
 * Implement the Dap4 Parse Actions
 */

public class Dap4ParserImpl extends Dap4BisonParser implements Dap4Parser
{

    //////////////////////////////////////////////////
    // Constants

    //////////////////////////////////////////////////
    // static variables

    static protected int globaldebuglevel = 0;

    //////////////////////////////////////////////////
    // Static methods

    static public void setGlobalDebugLevel(int level)
    {
        globaldebuglevel = level;
    }

    //////////////////////////////////////////////////
    // Instance variables

    protected DapFactory factory = null;

    protected ErrorResponse errorresponse = null;

    protected Deque<DapNode> scopestack = new ArrayDeque<DapNode>();

    protected DapDataset root = null; // of the parse

    protected boolean debug = false;

    //////////////////////////////////////////////////
    // Constructors

    public Dap4ParserImpl(DapFactory factory)
    {
        super();
        this.factory = factory; // see Dap4Actions
        if(globaldebuglevel > 0) setDebugLevel(globaldebuglevel);
    }

    //////////////////////////////////////////////////
    // Accessors

    public ErrorResponse getErrorResponse()
    {
        return errorresponse;
    }

    public DapDataset getDMR()
    {
        return this.root;
    }

    //////////////////////////////////////////////////
    // Parser API

    public boolean
    parse(String input)
            throws SAXException
    {
        return super.parse(input);
    }

    //////////////////////////////////////////////////
    // Parser specific methods

    DapGroup
    getGroupScope()
            throws DapException
    {
        DapGroup gscope = (DapGroup) searchScope(DapSort.GROUP, DapSort.DATASET);
        if(gscope == null) throw new DapException("Undefined Group Scope");
        return gscope;
    }

    DapNode
    getMetadataScope()
            throws DapException
    {
        // Search up the stack for first match.
        DapNode match = searchScope(METADATASCOPES);
        if(match == null)
            throw new ParseException("No enclosing metadata capable scope");
        return match;
    }

    DapNode
    getParentScope()
            throws DapException
    {
        DapNode parent = searchScope(DapSort.STRUCTURE, DapSort.SEQUENCE, DapSort.GROUP, DapSort.DATASET);
        if(parent == null) throw new DapException("Undefined parent Scope");
        return parent;
    }

    DapVariable
    getVariableScope()
            throws DapException
    {
        DapNode match = searchScope(DapSort.ATOMICVARIABLE, DapSort.STRUCTURE, DapSort.SEQUENCE);
        if(match == null)
            throw new ParseException("No enclosing variable scope");
        return (DapVariable) match;
    }

    DapNode
    getScope(DapSort... sort)
            throws DapException
    {
        DapNode node = searchScope(sort);
        if(node == null) // return exception if not found
            throw new ParseException("No enclosing scope of specified type");
        return node;
    }

    DapNode
    searchScope(DapSort... sort)
    {
        Iterator it = scopestack.iterator();
        while(it.hasNext()) {
            DapNode node = (DapNode) it.next();
            for(int j = 0; j < sort.length; j++) {
                if(node.getSort() == sort[j])
                    return node;
            }
        }
        return null;
    }

    DapVariable
    findVariable(DapNode parent, String name)
    {
        DapVariable var = null;
        switch (parent.getSort()) {
        case DATASET:
        case GROUP:
            var = ((DapGroup) parent).findVariable(name);
            break;
        case STRUCTURE:
            var = (DapVariable) ((DapStructure) parent).findByName(name);
            break;
        case SEQUENCE:
            var = (DapVariable) ((DapSequence) parent).findByName(name);
            break;
        default:
            break;
        }
        return var;
    }

    // Attribute map utilities
    SaxEvent
    pull(XMLAttributeMap map, String name)
    {
        SaxEvent event = map.remove(name.toLowerCase());
        return event;
    }

    // Attribute map utilities
    SaxEvent
    peek(XMLAttributeMap map, String name)
    {
        SaxEvent event = map.get(name.toLowerCase());
        return event;
    }

    //////////////////////////////////////////////////
    // Attribute construction

    DapAttribute
    makeAttribute(DapSort sort, String name, DapType basetype,
                  List<String> nslist, DapNode parent)
            throws DapException
    {
        DapAttribute attr = factory.newAttribute(name, basetype);
        if(sort == DapSort.ATTRIBUTE) {
            attr.setBaseType(basetype);
        }
        parent.addAttribute(attr);
        attr.setNamespaceList(nslist);
        return attr;
    }

    boolean isempty(SaxEvent token)
    {
        return token == null || isempty(token.value);
    }

    boolean isempty(String text)
    {
        return (text == null || text.length() == 0);
    }

    List<String>
    convertNamespaceList(NamespaceList nslist)
    {
        return nslist;
    }

    boolean
    islegalenumtype(DapType kind)
    {
        return kind.isIntegerType();
    }

    boolean
    islegalattributetype(DapType kind)
    {
        return kind.isLegalAttrType();
    }

    DapAttribute
    lookupAttribute(DapNode parent, XMLAttributeMap attrs)
            throws DapException
    {
        SaxEvent name = pull(attrs, "name");
        if(isempty(name))
            throw new ParseException("Attribute: Empty attribute name");
        String attrname = name.value;
        return parent.findAttribute(attrname);
    }

    void
    changeAttribute(DapAttribute attr, XMLAttributeMap description)
            throws DapException
    {
        SaxEvent name = pull(description, "name");
        if(isempty(name))
            throw new ParseException("Attribute: Empty attribute name");
        String attrname = name.value;
        if(!attr.getShortName().equals(attrname))
            throw new ParseException("Attribute: DATA DMR: Attribute name mismatch:" + name.name);
        switch (attr.getSort()) {
        case ATTRIBUTE:
            SaxEvent atype = pull(description, "type");
            SaxEvent value = pull(description, "value");
            String typename = (atype == null ? "Int32" : atype.value);
            if("Byte".equalsIgnoreCase(typename)) typename = "UInt8";
            DapType basetype = DapType.reify(typename);
            if(basetype != attr.getBaseType())
                throw new ParseException("Attribute: DATA DMR: Attempt to change attribute type: " + typename);
            attr.clearValues();
            if(value != null)
                attr.setValues(new Object[]{value.value});
            break;
        case ATTRIBUTESET:
            // clear the contained attributes
            attr.setAttributes(new HashMap<String, DapAttribute>());
            break;
        case OTHERXML:
            throw new ParseException("Attribute: DATA DMR: OtherXML attributes not supported");
        }
    }

    DapAttribute
    createatomicattribute(XMLAttributeMap attrs, NamespaceList nslist, DapNode parent)
            throws DapException
    {
        SaxEvent name = pull(attrs, "name");
        SaxEvent atype = pull(attrs, "type");
        if(false) { // if enable, then allow <Attribute type="..." value="..."/>
            SaxEvent value = pull(attrs, "value");
        }
        if(isempty(name))
            throw new ParseException("Attribute: Empty attribute name");
        String typename = (atype == null ? "Int32" : atype.value);
        if("Byte".equalsIgnoreCase(typename)) typename = "UInt8";
        DapType basetype = DapType.reify(typename);
        if(basetype == null || !islegalattributetype(basetype))
            throw new ParseException("Attribute: Invalid attribute type: " + typename);
        List<String> hreflist = convertNamespaceList(nslist);
        DapAttribute attr = makeAttribute(DapSort.ATTRIBUTE, name.value, basetype, hreflist, parent);
        return attr;
    }

    DapAttribute
    createcontainerattribute(XMLAttributeMap attrs, NamespaceList nslist, DapNode parent)
            throws DapException
    {
        SaxEvent name = pull(attrs, "name");
        if(isempty(name))
            throw new ParseException("ContainerAttribute: Empty attribute name");
        List<String> hreflist = convertNamespaceList(nslist);
        DapAttribute attr
                = makeAttribute(DapSort.ATTRIBUTESET, name.value, null, hreflist, parent);
        return attr;
    }

    void
    createvalue(String value, DapAttribute parent)
            throws DapException
    {
        parent.setValues(new Object[]{value});
    }

    void
    createvalue(SaxEvent value, DapAttribute parent)
            throws DapException
    {
        List<String> textlist = null;
        if(value.eventtype == SaxEventType.CHARACTERS) {
            textlist = ParseUtil.collectValues(value.text);
        } else if(value.eventtype == SaxEventType.ATTRIBUTE) {
            textlist = new ArrayList<String>();
            textlist.add(value.value);
        }
        if(textlist != null)
            parent.setValues(textlist.toArray());
    }

    DapOtherXML
    createotherxml(XMLAttributeMap attrs, DapNode parent)
            throws DapException
    {
        SaxEvent name = pull(attrs, "name");
        SaxEvent href = pull(attrs, "href");
        if(isempty(name))
            throw new ParseException("OtherXML: Empty name");
        List<String> nslist = new ArrayList<String>();
        if(!isempty(href))
            nslist.add(href.value);
        DapOtherXML other
                = (DapOtherXML) makeAttribute(DapSort.OTHERXML, name.value, null, nslist, parent);
        parent.setAttribute(other);
        return other;
    }

    //////////////////////////////////////////////////
    // Abstract action definitions
    @Override
    void
    enterdataset(XMLAttributeMap attrs)
            throws ParseException
    {
        this.debug = getDebugLevel() > 0; // make sure we have the latest value
        if(debug) report("enterdataset");
        SaxEvent name = pull(attrs, "name");
        SaxEvent dapversion = pull(attrs, "dapversion");
        SaxEvent dmrversion = pull(attrs, "dmrversion");
        if(isempty(name))
            throw new ParseException("Empty dataset name attribute");
        // convert and test version numbers
        float ndapversion = DAPVERSION;
        try {
            ndapversion = Float.parseFloat(dapversion.value);
        } catch (NumberFormatException nfe) {
            ndapversion = DAPVERSION;
        }
        if(ndapversion != DAPVERSION)
            throw new ParseException("Dataset dapVersion mismatch: " + dapversion.value);
        float ndmrversion = DAPVERSION;
        try {
            ndmrversion = Float.parseFloat(dmrversion.value);
        } catch (NumberFormatException nfe) {
            ndmrversion = DMRVERSION;
        }
        if(ndmrversion != DMRVERSION)
            throw new ParseException("Dataset dmrVersion mismatch: " + dmrversion.value);
        this.root = factory.newDataset(name.value);
        this.root.setDapVersion(Float.toString(ndapversion));
        this.root.setDMRVersion(Float.toString(ndmrversion));
        this.root.setDataset(this.root);
        scopestack.push(this.root);
    }

    @Override
    void
    leavedataset()
            throws ParseException
    {
        if(debug) report("leavedataset");
        assert (scopestack.peek() != null && scopestack.peek().getSort() == DapSort.DATASET);
        this.root.sort();
        scopestack.pop();
        if(!scopestack.isEmpty())
            throw new ParseException("Dataset: nested dataset");
        this.root.finish();
    }

    @Override
    void
    entergroup(SaxEvent name)
            throws ParseException
    {
        if(debug) report("entergroup");
        try {
            if(isempty(name))
                throw new ParseException("Empty group name");
            DapGroup parent = getGroupScope();
            DapGroup group;
            group = factory.newGroup(name.value);
            parent.addDecl(group);
            scopestack.push(group);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    leavegroup()
            throws ParseException
    {
        if(debug) report("leavegroup");
        scopestack.pop();
    }

    @Override
    void
    enterenumdef(XMLAttributeMap attrs)
            throws ParseException
    {
        if(debug) report("enterenumdef");
        try {
            SaxEvent name = pull(attrs, "name");
            if(isempty(name))
                throw new ParseException("Enumdef: Empty Enum Declaration name");

            SaxEvent basetype = pull(attrs, "basetype");
            DapType basedaptype = null;
            if(basetype == null) {
                basedaptype = DapEnumeration.DEFAULTBASETYPE;
            } else {
                String typename = basetype.value;
                if("Byte".equalsIgnoreCase(typename)) typename = "UInt8";
                basedaptype = DapType.reify(typename);
                basedaptype.toString();
                if(basedaptype == null || !islegalenumtype(basedaptype))
                    throw new ParseException("Enumdef: Invalid Enum Declaration Type name: " + basetype.value);
            }
            DapEnumeration dapenum = null;
            dapenum = factory.newEnumeration(name.value, basedaptype);
            DapGroup parent = getGroupScope();
            parent.addDecl(dapenum);
            scopestack.push(dapenum);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }


    @Override
    void
    leaveenumdef()
            throws ParseException
    {
        if(debug) report("leaveenumdef");
        DapEnumeration eparent = (DapEnumeration) scopestack.pop();
        List<String> econsts = eparent.getNames();
        if(econsts.size() == 0)
            throw new ParseException("Enumdef: no enum constants specified");
    }

    @Override
    void
    enumconst(SaxEvent name, SaxEvent value)
            throws ParseException
    {
        if(debug) report("enumconst");
        if(isempty(name))
            throw new ParseException("Enumconst: Empty enum constant name");
        if(isempty(value))
            throw new ParseException("Enumdef: Invalid enum constant value: " + value.value);
        long lvalue = 0;
        try {
            BigInteger bivalue = new BigInteger(value.value);
            bivalue = DapUtil.BIG_UMASK64.and(bivalue);
            lvalue = bivalue.longValue();
        } catch (NumberFormatException nfe) {
            throw new ParseException("Enumconst: illegal value: " + value.value);
        }
        try {
            DapEnumeration parent = (DapEnumeration) getScope(DapSort.ENUMERATION);
            // Verify that the name is a legal enum constant name, which is restricted
            // vis-a-vis other names
            if(!ParseUtil.isLegalEnumConstName(name.value))
                throw new ParseException("Enumconst: illegal enumeration constant name: " + name.value);
            parent.addEnumConst(factory.newEnumConst(name.value, lvalue));
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    enterdimdef(XMLAttributeMap attrs)
            throws ParseException
    {
        if(debug) report("enterdimdef");
        SaxEvent name = pull(attrs, "name");
        SaxEvent size = pull(attrs, "size");
        long lvalue = 0;
        if(isempty(name))
            throw new ParseException("Dimdef: Empty dimension declaration name");
        if(isempty(size))
            throw new ParseException("Dimdef: Empty dimension declaration size");
        try {
            lvalue = Long.parseLong(size.value);
            if(lvalue <= 0)
                throw new ParseException("Dimdef: value <= 0: " + lvalue);
        } catch (NumberFormatException nfe) {
            throw new ParseException("Dimdef: non-integer value: " + size.value);
        }
        DapDimension dim = null;
        try {

            dim = factory.newDimension(name.value, lvalue);
            dim.setShared(true);
            DapGroup parent = getGroupScope();
            parent.addDecl(dim);

            scopestack.push(dim);
        } catch (
                DapException de
                )

        {
            throw new ParseException(de);
        }

    }

    @Override
    void
    leavedimdef()
            throws ParseException
    {
        if(debug) report("leavedimdef");
        scopestack.pop();
    }

    @Override
    void
    dimref(SaxEvent nameorsize)
            throws ParseException
    {
        if(debug) report("dimref");
        try {
            DapDimension dim = null;
            DapVariable var = getVariableScope();
            assert var != null : "Internal error";

            boolean isname = nameorsize.name.equals("name");
            if(isname && isempty(nameorsize))
                throw new ParseException("Dimref: Empty dimension reference name");
            else if(isempty(nameorsize))
                throw new ParseException("Dimref: Empty dimension size");
            if(isname) {
                DapGroup dg = var.getGroup();
                if(dg == null)
                    throw new ParseException("Internal error: variable has no containing group");
                DapGroup grp = var.getGroup();
                if(grp == null)
                    throw new ParseException("Variable has no group");
                dim = (DapDimension) grp.findByFQN(nameorsize.value, DapSort.DIMENSION);
            } else {// Size only is given; presume a number; create unique anonymous dimension
                String ssize = nameorsize.value.trim();
                if(ssize.equals("*"))
                    dim = DapDimension.VLEN;
                else {
                    // Note that we create it in the root group
                    assert (root != null);
                    long anonsize;
                    try {
                        anonsize = Long.parseLong(nameorsize.value.trim());
                    } catch (NumberFormatException nfe) {
                        throw new ParseException("Dimref: Illegal dimension size");
                    }
                    dim = root.createAnonymous(anonsize);
                }
            }
            if(dim == null)
                throw new ParseException("Unknown dimension: " + nameorsize.value);
            var.addDimension(dim);
        } catch (DapException de) {
            throw new ParseException(de.getMessage(), de.getCause());
        }
    }

    @Override
    void
    enteratomicvariable(SaxEvent open, SaxEvent name)
            throws ParseException
    {
        if(debug) report("enteratomicvariable");
        try {
            if(isempty(name))
                throw new ParseException("Atomicvariable: Empty dimension reference name");
            String typename = open.name;
            if("Byte".equals(typename)) typename = "UInt8"; // special case
            DapType basetype = DapType.reify(typename);
            if(basetype == null)
                throw new ParseException("AtomicVariable: Illegal type: " + open.name);
            DapVariable var = null;
            // Do type substitutions
            var = factory.newAtomicVariable(name.value, basetype);
            // Look at the parent scope
            DapNode parent = scopestack.peek();
            if(parent == null)
                throw new ParseException("Variable has no parent");
            switch (parent.getSort()) {
            case DATASET:
            case GROUP:
                ((DapGroup) parent).addDecl(var);
                break;
            case STRUCTURE:
                ((DapStructure) parent).addField(var);
                break;
            case SEQUENCE:
                ((DapSequence) parent).addField(var);
                break;
            default:
                assert false : "Atomic variable in illegal scope";
            }
            scopestack.push(var);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    void openclosematch(SaxEvent close, DapSort sort)
            throws ParseException
    {
        String typename = close.name;
        if("Byte".equals(typename)) typename = "UInt8"; // special case
        switch (sort) {
        case ATOMICVARIABLE:
            TypeSort atype = TypeSort.getTypeSort(typename);
            DapVariable var = (DapVariable) searchScope(sort);
            assert var != null;
            TypeSort vartype = var.getBaseType().getTypeSort();
            if(atype == null)
                throw new ParseException("Variable: Illegal type: " + typename);
            if(atype != vartype)
                throw new ParseException(String.format("variable: open/close type mismatch: <%s> </%s>",
                        vartype, atype));
            break;
        case SEQUENCE:
        case STRUCTURE:
            if(!sort.getName().equalsIgnoreCase(typename))
                throw new ParseException(String.format("variable: open/close type mismatch: <%s> </%s>",
                        typename, sort.getName()));
            break;
        default:
            throw new ParseException("Variable: Illegal type: " + typename);
        }
    }

    void leavevariable()
            throws ParseException
    {
        scopestack.pop();
    }

    void
    leaveatomicvariable(SaxEvent close)
            throws ParseException
    {
        openclosematch(close, DapSort.ATOMICVARIABLE);
        leavevariable();
    }

    @Override
    void
    enterenumvariable(XMLAttributeMap attrs)
            throws ParseException
    {
        if(debug) report("enterenumvariable");
        try {
            SaxEvent name = pull(attrs, "name");
            SaxEvent enumtype = pull(attrs, "enum");
            if(isempty(name))
                throw new ParseException("Enumvariable: Empty variable name");
            if(isempty(enumtype))
                throw new ParseException("Enumvariable: Empty enum type name");
            DapEnumeration target = (DapEnumeration) root.findByFQN(enumtype.value, DapSort.ENUMERATION);
            if(target == null)
                throw new ParseException("EnumVariable: no such enum: " + name.value);
            DapVariable var = null;
            var = factory.newAtomicVariable(name.value, target);
            // Look at the parent scope
            DapNode parent = scopestack.peek();
            if(parent == null)
                throw new ParseException("Variable has no parent");
            switch (parent.getSort()) {
            case DATASET:
            case GROUP:
                ((DapGroup) parent).addDecl(var);
                break;
            case STRUCTURE:
                ((DapStructure) parent).addField(var);
                break;
            case SEQUENCE:
                ((DapSequence) parent).addField(var);
                break;
            default:
                assert false : "Atomic variable in illegal scope";
            }
            scopestack.push(var);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    leaveenumvariable(SaxEvent close)
            throws ParseException
    {
        if(debug) report("leaveenumvariable");
        openclosematch(close, DapSort.ATOMICVARIABLE);
        leavevariable();
    }

    @Override
    void
    entermap(SaxEvent name)
            throws ParseException
    {
        if(debug) report("entermap");
        if(isempty(name))
            throw new ParseException("Mapref: Empty map name");
        DapAtomicVariable var;
        try {
            var = (DapAtomicVariable) root.findByFQN(name.value, DapSort.ATOMICVARIABLE);
        } catch (DapException de) {
            throw new ParseException(de);
        }
        if(var == null)
            throw new ParseException("Mapref: undefined variable: " + name.name);
        // Verify that this is a legal map =>
        // 1. it is outside the scope of its parent if the parent
        //    is a structure.
        DapNode container = var.getContainer();
        DapNode scope;
        try {
            scope = getParentScope();
        } catch (DapException de) {
            throw new ParseException(de);
        }
        if((container.getSort() == DapSort.STRUCTURE || container.getSort() == DapSort.SEQUENCE)
                && container == scope)
            throw new ParseException("Mapref: map variable not in outer scope: " + name.name);
        DapMap map = factory.newMap(var);
        try {
            // Pull the top variable scope
            DapVariable parent = (DapVariable) searchScope(DapSort.ATOMICVARIABLE, DapSort.STRUCTURE, DapSort.SEQUENCE);
            if(parent == null)
                throw new ParseException("Variable has no parent: " + var);
            parent.addMap(map);
        } catch (DapException de) {
            throw new ParseException(de);
        }
        scopestack.push(map);
    }

    @Override
    void
    leavemap()
            throws ParseException
    {
        if(debug) report("leavemap");
        scopestack.pop();
    }

    @Override
    void
    enterstructurevariable(SaxEvent name)
            throws ParseException
    {
        if(debug) report("enterstructurevariable");
        if(isempty(name))
            throw new ParseException("Structure: Empty structure name");
        try {
            DapStructure var = null;
            var = factory.newStructure(name.value);
            // Look at the parent scope
            DapNode parent = scopestack.peek();
            if(parent == null)
                throw new ParseException("Variable has no parent");
            switch (parent.getSort()) {
            case DATASET:
            case GROUP:
                ((DapGroup) parent).addDecl(var);
                break;
            case STRUCTURE:
            case SEQUENCE:
                ((DapStructure) parent).addField(var);
                break;
            default:
                assert false : "Structure variable in illegal scope";
            }
            scopestack.push(var);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    leavestructurevariable(SaxEvent close)
            throws ParseException
    {
        if(debug) report("leavestructurevariable");
        openclosematch(close, DapSort.STRUCTURE);
        leavevariable();
    }

    @Override
    void
    entersequencevariable(SaxEvent name)
            throws ParseException
    {
        if(debug) report("entersequencevariable");
        if(isempty(name))
            throw new ParseException("Sequence: Empty sequence name");
        try {
            DapVariable var = null;
            var = factory.newSequence(name.value);
            // Look at the parent scope
            DapNode parent = scopestack.peek();
            if(parent == null)
                throw new ParseException("Variable has no parent");
            switch (parent.getSort()) {
            case DATASET:
            case GROUP:
                ((DapGroup) parent).addDecl(var);
                break;
            case STRUCTURE:
                ((DapStructure) parent).addField(var);
                break;
            case SEQUENCE:
                ((DapSequence) parent).addField(var);
                break;
            default:
                assert false : "Structure variable in illegal scope";
            }
            scopestack.push(var);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    leavesequencevariable(SaxEvent close)
            throws ParseException
    {
        if(debug) report("leavesequencevariable");
        openclosematch(close, DapSort.SEQUENCE);
        leavevariable();
    }

    @Override
    void
    enteratomicattribute(XMLAttributeMap attrs, NamespaceList nslist)
            throws ParseException
    {
        if(debug) report("enteratomicattribute");
        try {
            DapNode parent = getMetadataScope();
            DapAttribute attr = null;
            attr = createatomicattribute(attrs, nslist, parent);
            scopestack.push(attr);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    leaveatomicattribute()
            throws ParseException
    {
        if(debug) report("leaveatomicattribute");
        DapAttribute attr = (DapAttribute) scopestack.pop();
        // Ensure that the attribute has at least one value
        if(attr.getValues().length == 0)
            throw new ParseException("AtomicAttribute: attribute has no values");
    }

    @Override
    void
    entercontainerattribute(XMLAttributeMap attrs, NamespaceList nslist)
            throws ParseException
    {
        if(debug) report("entercontainerattribute");
        try {
            DapNode parent = getMetadataScope();
            DapAttribute attr = null;
            attr = createcontainerattribute(attrs, nslist, parent);
            scopestack.push(attr);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    leavecontainerattribute()
            throws ParseException
    {
        if(debug) report("leavecontainerattribute");
        scopestack.pop();
    }

    @Override
    void value(String value)
            throws ParseException
    {
        if(debug) report("value");
        try {
            DapAttribute parent = (DapAttribute) getScope(DapSort.ATTRIBUTE);
            createvalue(value, parent);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void value(SaxEvent value)
            throws ParseException
    {
        if(debug) report("value");
        try {
            DapAttribute parent = (DapAttribute) getScope(DapSort.ATTRIBUTE);
            createvalue(value, parent);
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    void
    otherxml(XMLAttributeMap attrs, DapXML root)
            throws ParseException
    {
        if(debug) report("enterotherxml");
        try {
            DapNode parent = getMetadataScope();
            DapOtherXML other = createotherxml(attrs, parent);
            parent.setAttribute(other);
            other.setRoot(root);
            if(debug) report("leaveotherxml");
        } catch (DapException de) {
            throw new ParseException(de);
        }
    }

    @Override
    DapXML.XMLList
    xml_body(DapXML.XMLList body, DapXML elemortext)
            throws ParseException
    {
        if(debug) report("xml_body.enter");
        if(body == null) body = new DapXML.XMLList();
        if(elemortext != null)
            body.add(elemortext);
        if(debug) report("xml_body.exit");
        return body;
    }

    @Override
    DapXML
    element_or_text(SaxEvent open, XMLAttributeMap map, DapXML.XMLList body, SaxEvent close)
            throws ParseException
    {
        try {
            if(debug) report("element_or_text.enter");
            if(!open.name.equalsIgnoreCase(close.name))
                throw new ParseException(
                        String.format("OtherXML: mismatch: <%s> vs </%s>", open.name, close.name));
            DapXML thisxml = createxmlelement(open, map);
            for(DapXML xml : body) {
                thisxml.addElement(xml);
            }
            if(debug) report("element_or_text.exit");
            return thisxml;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    @Override
    DapXML
    xmltext(SaxEvent text)
            throws ParseException
    {
        try {
            if(debug) report("xmltext");
            DapXML txt = createxmltext(text.text);
            return txt;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    @Override
    void
    entererror(XMLAttributeMap attrs)
            throws ParseException
    {
        if(debug) report("entererror");
        SaxEvent xhttpcode = pull(attrs, "httpcode");
        String shttpcode = (xhttpcode == null ? "400" : xhttpcode.value);
        int httpcode = 0;
        try {
            httpcode = Integer.parseInt(shttpcode);
        } catch (NumberFormatException nfe) {
            throw new ParseException("Error Response; illegal http code: " + shttpcode);
        }
        this.errorresponse = new ErrorResponse();
        this.errorresponse.setCode(httpcode);
    }

    @Override
    void
    leaveerror()
            throws ParseException
    {
        if(debug) report("leaveerror");
        assert (this.errorresponse != null) : "Internal Error";
    }

    @Override
    void
    errormessage(String value)
            throws ParseException
    {
        if(debug) report("errormessage");
        assert (this.errorresponse != null) : "Internal Error";
        String message = value;
        message = Escape.entityUnescape(message); // Remove XML encodings
        this.errorresponse.setMessage(message);
    }

    @Override
    void
    errorcontext(String value)
            throws ParseException
    {
        if(debug) report("errorcontext");
        assert (this.errorresponse != null) : "Internal Error";
        String context = value;
        context = Escape.entityUnescape(context); // Remove XML encodings
        this.errorresponse.setContext(context);
    }

    @Override
    void
    errorotherinfo(String value)
            throws ParseException
    {
        if(debug) report("errorotherinfo");
        assert (this.errorresponse != null) : "Internal Error";
        String other = value;
        other = Escape.entityUnescape(other); // Remove XML encodings
        this.errorresponse.setOtherInfo(other);
    }

    @Override
    String
    textstring(String prefix, SaxEvent text)
            throws ParseException
    {
        if(debug) report("text");
        if(prefix == null)
            return text.text;
        else
            return prefix + text.text;
    }

    //////////////////////////////////////////////////
    // Utilities

    void report(String action)
    {
        getDebugStream().println("ACTION: " + action);
        getDebugStream().flush();
    }
}