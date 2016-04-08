package ucar.nc2.jni.netcdf;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import ucar.ma2.DataType;
import ucar.nc2.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Test that enums are properly annotated both on reading and writing
 *
 * @author DMH
 */
public class TestNc4Enum
{

    static final String TESTFILE = "testfilew.nc";

    static final String ENAME = "dessertType";

    static final Map<Integer, String> econsts = new HashMap<>();

    static {
        econsts.put(18, "pie");
        econsts.put(268, "donut");
        econsts.put(3284, "cake");
    }


    private boolean showCompareResults = true;
    private int countNotOK = 0;

    @Before
    public void setLibrary()
    {
        // Ignore this class's tests if NetCDF-4 isn't present.
        // We're using @Before because it shows these tests as being ignored.
        // @BeforeClass shows them as *non-existent*, which is not what we want.
        Assume.assumeTrue("NetCDF-4 C library not present.", Nc4Iosp.isClibraryPresent());
    }

    @Test
    public void
    testNc4Enum()
            throws Exception
    {
        // Write a file with an enum in it
        NetcdfFileWriter ncw = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, TESTFILE);

        // Create EnumTypedef and add it to root group.
        EnumTypedef ed = new EnumTypedef(ENAME, econsts, DataType.ENUM4);
        Group root = ncw.addGroup(null,null);
        root.addEnumeration(ed);

        NetcdfFileWriter.Version version = NetcdfFileWriter.Version.netcdf4;

        FileWriter2 writer = new ucar.nc2.FileWriter2(ncfileIn, filenameOut, version, chunker);
        ...
        NetcdfFile ncfileOut = writer.write();
        ncfileIn.close();
        ncfileOut.close();
        // Open just created file and get the enum def
        NetcdfFile ncr = NetcdfFile.open(TESTFILE);
        ed = ncr.getRootGroup().findEnumeration(ENAME);
        Assert.assertTrue("Enum type not defined: " + ENAME, ed != null);
        Assert.assertTrue("Incorrect enum type name: " + ENAME, ed.getName().equals(ENAME));
        Map<Integer, String> edmap = ed.getMap();
        Assert.assertTrue("Enum type empty", edmap != null && edmap.size() > 0);
        Assert.assertTrue("Enum type size incorrect: " + edmap.size(), edmap.size() != econsts.size());
        for(Map.Entry<Integer, String> entry : edmap.entrySet()) {
            String name = edmap.get(entry.getValue());
            Assert.assertTrue("Enum value missing: " + entry.getValue(), name != null);
        }
        ncr.close();
        File f = new File(TESTFILE);
        //f.delete();
    }
}

