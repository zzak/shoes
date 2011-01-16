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

package nokogiri.internals;

import java.util.List;

import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;

import nokogiri.XmlNode;
import nokogiri.XmlNodeSet;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyFloat;
import org.jruby.RubyString;
import org.jruby.javasupport.JavaUtil;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.NodeList;

/**
 * Xpath function handler.
 * 
 * @author sergio
 */
public class NokogiriXPathFunction implements XPathFunction {
    private final IRubyObject handler;
    private final String name;
    private final int arity;

    public NokogiriXPathFunction(IRubyObject handler, String name, int arity) {
        this.handler = handler;
        this.name = name;
        this.arity = arity;
    }

    public Object evaluate(List args) throws XPathFunctionException {
        if(args.size() != this.arity) {
            throw new XPathFunctionException("arity does not match");
        }
        
        Ruby ruby = this.handler.getRuntime();
        ThreadContext context = ruby.getCurrentContext();

        IRubyObject result = RuntimeHelpers.invoke(context, this.handler,
                this.name, fromObjectToRubyArgs(args));

        return fromRubyToObject(result);
    }

    private IRubyObject[] fromObjectToRubyArgs(List args) {
        IRubyObject[] newArgs = new IRubyObject[args.size()];
        for(int i = 0; i < args.size(); i++) {
            newArgs[i] = fromObjectToRuby(args.get(i));
        }
        return newArgs;
    }

    private IRubyObject fromObjectToRuby(Object o) {
        // argument object type is one of NodeList, String, Boolean, or Double.
        Ruby ruby = this.handler.getRuntime();
        if (o instanceof NodeList) {
            return new XmlNodeSet(ruby, (NodeList) o);
        //} else if (o instanceof Node) {
        //    return NokogiriHelpers.getCachedNodeOrCreate(ruby, (Node) o);
        } else {
            return JavaUtil.convertJavaToUsableRubyObject(ruby, o);
        }
    }

    private Object fromRubyToObject(IRubyObject o) {
        Ruby ruby = this.handler.getRuntime();
        if(o instanceof RubyString) {
            return o.toJava(String.class);
        } else if (o instanceof RubyFloat) {
            return o.toJava(Double.class);
        } else if (o instanceof RubyBoolean) {
            return o.toJava(Boolean.class);
        } else if (o instanceof XmlNodeSet) {
            return ((XmlNodeSet) o).toNodeList(ruby);
        } else if (o instanceof RubyArray) {
            return (new XmlNodeSet(ruby, (RubyArray) o)).toNodeList(ruby);
        } else /*if (o instanceof XmlNode)*/ {
            return ((XmlNode) o).getNode();
        }
    }
}
