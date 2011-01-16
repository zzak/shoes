/**
 * (The MIT License)
 *
 * Copyright (c) 2008 - 2010:
 *
 * * {Aaron Patterson}[http://tenderlovemaking.com]
 * * {Mike Dalessio}[http://mike.daless.io]
 * * {Charles Nutter}[http://blog.headius.com]
 * * {Sergio Arbeo}[http://www.serabe.com]
 * * {Patrick Mahoney}[http://polycrystal.org]
 * * {Yoko Harada}[http://yokolet.blogspot.com]
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * 'Software'), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nokogiri;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import nokogiri.internals.NokogiriNamespaceContext;
import nokogiri.internals.NokogiriXPathFunctionResolver;
import nokogiri.internals.NokogiriXPathVariableResolver;

import org.jruby.Ruby;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.NodeList;

/**
 * Class for Nokogiri::XML::XpathContext
 *
 */
@JRubyClass(name="Nokogiri::XML::XPathContext")
public class XmlXpathContext extends RubyObject {
    private XmlNode context;
    private XPath xpath;

    public XmlXpathContext(Ruby ruby, RubyClass rubyClass, XmlNode context) {
        super(ruby, rubyClass);
        this.context = context;
        this.xpath = XPathFactory.newInstance().newXPath();
        this.xpath.setNamespaceContext(new NokogiriNamespaceContext());
        this.xpath.setXPathVariableResolver(new NokogiriXPathVariableResolver());
    }

    @JRubyMethod(name = "new", meta = true)
    public static IRubyObject rbNew(ThreadContext context, IRubyObject cls, IRubyObject node) {
        XmlNode xmlNode = (XmlNode)node;
        return new XmlXpathContext(context.getRuntime(), (RubyClass)cls, xmlNode);
    }

    @JRubyMethod
    public IRubyObject evaluate(ThreadContext context, IRubyObject expr, IRubyObject handler) {
        String src = expr.convertToString().asJavaString();
        try {
            if(!handler.isNil()) {
            	if (!isContainsPrefix(src)) {
                    Set<String> methodNames = handler.getMetaClass().getMethods().keySet();
                    for (String name : methodNames) {
                        src = src.replaceAll(name, NokogiriNamespaceContext.NOKOGIRI_PREFIX+":"+name);
                    }
                }
                xpath.setXPathFunctionResolver(new NokogiriXPathFunctionResolver(handler));
            }
            XPathExpression xpathExpression = xpath.compile(src);
            return node_set(context, xpathExpression);
            //return new XmlXpath(context.getRuntime(), (RubyClass)context.getRuntime().getClassFromPath("Nokogiri::XML::XPath"), xpathExpression, this.context);
        } catch (XPathExpressionException xpee) {
            xpee = new XPathExpressionException(src);
            RubyException e =
                XmlSyntaxError.createXPathSyntaxError(getRuntime(), xpee);
            throw new RaiseException(e);
        }
    }

    protected IRubyObject node_set(ThreadContext rbctx, XPathExpression xpathExpression) {
        XmlNodeSet result = null;
        try {  
            result = tryGetNodeSet(xpathExpression);
//            result.relink_namespace(context);
            result.setDocument(context.document(rbctx));
            return result;
        } catch (XPathExpressionException xpee) {
            try {
                return tryGetOpaqueValue(xpathExpression);
            } catch (XPathExpressionException xpee_opaque) {
                 RubyException e = XmlSyntaxError.createXPathSyntaxError(getRuntime(), xpee_opaque);
                 throw new RaiseException(e);
            }
        }
    }
    
    private XmlNodeSet tryGetNodeSet(XPathExpression xpathExpression) throws XPathExpressionException {
        NodeList nodes = (NodeList)xpathExpression.evaluate(context.node, XPathConstants.NODESET);
        return new XmlNodeSet(getRuntime(), nodes);       
    }
    
    private static Pattern number_pattern = Pattern.compile("\\d.*");
    private static Pattern boolean_pattern = Pattern.compile("true|false");
    
    private IRubyObject tryGetOpaqueValue(XPathExpression xpathExpression) throws XPathExpressionException {
        String string = (String)xpathExpression.evaluate(context.node, XPathConstants.STRING);
        if (doesMatch(number_pattern, string)) return RubyNumeric.dbl2num(getRuntime(), Double.parseDouble(string));
        if (doesMatch(boolean_pattern, string)) return RubyBoolean.newBoolean(getRuntime(), Boolean.parseBoolean(string));
        return RubyString.newString(getRuntime(), string);
    }
    
    private boolean doesMatch(Pattern pattern, String string) {
        Matcher m = pattern.matcher(string);
        return m.matches();
    }

    private boolean isContainsPrefix(String str) {
        Set<String> prefixes = ((NokogiriNamespaceContext)xpath.getNamespaceContext()).getAllPrefixes();
        for (String prefix : prefixes) {
            if (str.contains(prefix + ":")) {
                return true;
            }
        }
        return false;
    }


    @JRubyMethod
    public IRubyObject evaluate(ThreadContext context, IRubyObject expr) {
        return this.evaluate(context, expr, context.getRuntime().getNil());
    }

    @JRubyMethod
    public IRubyObject register_ns(ThreadContext context, IRubyObject prefix, IRubyObject uri) {
        ((NokogiriNamespaceContext) this.xpath.getNamespaceContext()).registerNamespace(prefix.convertToString().asJavaString(), uri.convertToString().asJavaString());
        return this;
    }

    @JRubyMethod
    public IRubyObject register_variable(ThreadContext context, IRubyObject name, IRubyObject value) {
        ((NokogiriXPathVariableResolver) this.xpath.getXPathVariableResolver()).
            registerVariable(name.convertToString().asJavaString(), value.convertToString().asJavaString());
        return this;
    }
}
