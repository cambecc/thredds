/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.test;

import dap4.core.util.DapException;
import dap4.core.util.DapUtil;
import dap4.servlet.DapController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import thredds.server.dap4.Dap4Controller;
import ucar.httpservices.HTTPUtil;
import ucar.nc2.util.CommonTestUtils;
import ucar.unidata.test.util.TestDir;

import javax.servlet.ServletException;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.Set;

@ContextConfiguration
@WebAppConfiguration("file:src/test/data")
abstract public class DapTestCommon
{
    //////////////////////////////////////////////////
    // Constants

    static final String DEFAULTTREEROOT = "dap4";

    static public final String FILESERVER = "file://localhost:8080";

    static public final String CONSTRAINTTAG = "dap4.ce";

    static final String D4TESTDIRNAME = "d4tests";

    // Equivalent to the path to the webapp/d4ts for testing purposes
    static protected final String DFALTRESOURCEPATH = "/src/test/data/resources";
    //////////////////////////////////////////////////
    // Type decls

    static  class Mocker
    {
        public MockHttpServletRequest req = null;
        public MockHttpServletResponse resp = null;
        public MockServletContext context = null;
        public DapController controller = null;
        public String url = null;
        public String servletname = null;
        public DapTestCommon parent = null;

        public DapController controller = null;

        public Mocker(String servletname, String url, DapTestCommon parent)
                throws Exception
        {
            this(servletname,url,new Dap4Controller(),parent);
        }

        public Mocker(String servletname, String url, DapController controller, DapTestCommon parent)
                        throws Exception
                {
            this.parent = parent;
            this.url = url;
            this.servletname = servletname;
            if(controller != null)
                setController(controller);
            String testdir = parent.getResourceRoot();
            // There appears to be bug in the spring core.io code
            // such that it assumes absolute paths start with '/'.
            // So, check for windows drive and prepend 'file:/' as a hack.
            if(DapUtil.hasDriveLetter(testdir))
                testdir = "/" + testdir;
            testdir = "file:" + testdir;
            this.context = new MockServletContext(testdir);
            URI u = HTTPUtil.parseToURI(url);
            this.req = new MockHttpServletRequest(this.context, "GET", u.getPath());
            this.resp = new MockHttpServletResponse();
            req.setMethod("GET");
            setup();
        }

        protected void
        setController(DapController ct)
                throws ServletException
        {
            this.controller = ct;
            this.controller.TESTING = true;
        }

        /**
         * The spring mocker is not very smart.
         * Given the url it should be possible
         * to initialize a lot of its fields.
         * Instead, it requires the user to so do.
         */
        protected void setup()
                throws Exception
        {
            this.req.setCharacterEncoding("UTF-8");
            this.req.setServletPath("/" + this.servletname);
            URI url = HTTPUtil.parseToURI(this.url);
            this.req.setProtocol(url.getScheme());
            this.req.setQueryString(url.getQuery());
            this.req.setServerName(url.getHost());
            this.req.setServerPort(url.getPort());
            String path = url.getPath();
            if(path != null) {// probably more complex than it needs to be
                String prefix = null;
                String suffix = null;
                String spiece = "/" + servletname;
                if(path.equals(spiece) || path.equals(spiece + "/")) {
                    // path is just spiece
                    prefix = spiece;
                    suffix = "/";
                } else {

                    String[] pieces = path.split(spiece + "/"); // try this first
                    if(pieces.length == 1 && path.endsWith(spiece))
                        pieces = path.split(spiece);  // try this
                    switch (pieces.length) {
                    case 0:
                        throw new IllegalArgumentException("CommonTestUtils");
                    case 1:
                        prefix = pieces[0] + spiece;
                        suffix = "";
                        break;
                    default: // > 1
                        prefix = pieces[0] + spiece;
                        suffix = path.substring(prefix.length());
                        break;
                    }
                }
                this.req.setContextPath(prefix);
                this.req.setPathInfo(suffix);
            }
            if(i == pieces.length)
                throw new IllegalArgumentException("Bad mock uri path: " + path);
            String cp = "/" + DapUtil.join(pieces, "/", 0, i);
            String sp = "/" + DapUtil.join(pieces, "/", i, pieces.length);
            this.req.setContextPath(cp);
            this.req.setServletPath(sp);
        }

        public byte[] execute()
                throws IOException
        {
            if(this.controller == null)
                throw new DapException("Mocker: no controller");
            this.controller.handleRequest(this.req, this.resp);
            return this.resp.getContentAsByteArray();
        }
    }

    //////////////////////////////////////////////////
    // Static methods

    static String
    locateDAP4Root(String threddsroot)
    {
        String root = threddsroot;
        if(root != null)
            root = root + "/" + DEFAULTTREEROOT;
        // See if it exists
        File f = new File(root);
        if(!f.exists() || !f.isDirectory())
            root = null;
        return root;
    }

    //////////////////////////////////////////////////
    // Instance variables

    // System properties
    protected boolean prop_ascii = true;
    protected boolean prop_diff = true;
    protected boolean prop_baseline = false;
    protected boolean prop_visual = false;
    protected boolean prop_debug = DEBUG;
    protected boolean prop_generate = true;
    protected String prop_controls = null;

    // Define a tree pattern to recognize the root.
    protected String threddsroot = null;
    protected String dap4root = null;

    protected String title = "Testing";

    public DapTestCommon()
    {
        this("DapTest");
    }

    public DapTestCommon(String name)
    {
        super(name);
        this.dap4root = locateDAP4Root(this.threddsroot);
        if(this.dap4root == null)
            System.err.println("Cannot locate /dap4 parent dir");
        this.dap4testroot = dap4root + "/" + D4TESTDIRNAME;
        // Compute the set of SOURCES
        this.d4tsServer = TestDir.dap4TestServer;
        if(DEBUG)
            System.err.println("DapTestCommon: d4tsServer=" + d4tsServer);
    }

    /**
     * Try to get the system properties
     */
    protected void setSystemProperties()
    {
        String testargs = System.getProperty("testargs");
        if(testargs != null && testargs.length() > 0) {
            String[] pairs = testargs.split("[  ]*[,][  ]*");
            for(String pair : pairs) {
                String[] tuple = pair.split("[  ]*[=][  ]*");
                String value = (tuple.length == 1 ? "" : tuple[1]);
                if(tuple[0].length() > 0)
                    System.setProperty(tuple[0], value);
            }
        }
        if(System.getProperty("nodiff") != null)
            prop_diff = false;
        if(System.getProperty("baseline") != null)
            prop_baseline = true;
        if(System.getProperty("nogenerate") != null)
            prop_generate = false;
        if(System.getProperty("debug") != null)
            prop_debug = true;
        if(System.getProperty("visual") != null)
            prop_visual = true;
        if(System.getProperty("ascii") != null)
            prop_ascii = true;
        if(System.getProperty("utf8") != null)
            prop_ascii = false;
        if(prop_baseline && prop_diff)
            prop_diff = false;
        prop_controls = System.getProperty("controls", "");
    }

    //////////////////////////////////////////////////
    // Overrideable methods

    protected String getD4TestsRoot()
    {
        return this.dap4testroot;
    }

    protected String getResourceRoot()
    {
        return getD4TestsRoot() + DFALTRESOURCEPATH;
    }

    //////////////////////////////////////////////////
    // Accessor

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return this.title;
    }

    //////////////////////////////////////////////////
    // Instance Utilities

    public void
    visual(String header, String captured)
    {
        if(!captured.endsWith("\n"))
            captured = captured + "\n";
        // Dump the output for visual comparison
        System.out.println("Testing " + title + ": " + header + ":");
        System.out.println("---------------");
        System.out.print(captured);
        System.out.println("---------------");
    }

    public boolean

    compare(String baselinecontent, String testresult)
            throws Exception
    {
        StringReader baserdr = new StringReader(baselinecontent);
        StringReader resultrdr = new StringReader(testresult);
        // Diff the two files
        Diff diff = new Diff("Testing " + getTitle());
        boolean pass = !diff.doDiff(baserdr, resultrdr);
        baserdr.close();
        resultrdr.close();
        return pass;
    }

    protected void
    findServer(String path)
            throws DapException
    {
        if(d4tsServer.startsWith("file:")) {
            d4tsServer = FILESERVER + "/" + path;
        } else {
            String svc = "http://" + d4tsServer + "/d4ts";
            if(!checkServer(svc))
                log.warn("D4TS Server not reachable: " + svc);
            // Since we will be accessing it thru NetcdfDataset, we need to change the schema.
            d4tsServer = "dap4://" + d4tsServer + "/d4ts";
        }
    }

    //////////////////////////////////////////////////

    public String getDAP4Root()
    {
        return this.dap4root;
    }

    @Override
    public String getResourceDir()
    {
        return this.resourcedir;
    }

}

