package net.liftweb.http.js

/*                                                *\
(c) 2007 WorldWide Conferencing, LLC
Distributed under an Apache License
http://www.apache.org/licenses/LICENSE-2.0
\*                                                 */

import scala.xml.{NodeSeq, Group, Unparsed, Elem}
import net.liftweb.util.Helpers._
import net.liftweb.util._

object JsCommands {
  def create = new JsCommands(Nil)  
  def apply(in: Seq[JsCmd]) = new JsCommands(in.toList.reverse)
}

class JsCommands(val reverseList: List[JsCmd]) extends ResponseIt {
  def ++(in: JsCmd) = new JsCommands(in :: reverseList)
  def ++(in: List[JsCmd]) = new JsCommands(in.reverse ::: reverseList)
  
  def toResponse = {
    val data = reverseList.reverse.map(_.toJsCmd).mkString("\n").getBytes("UTF-8")
    Response(data, List("Content-Length" -> data.length.toString, "Content-Type" -> "text/javascript"), 200)
  }
}

case class JsonCall(funcId: String) {
  def apply(command: String): JsCmd = apply(JE.Str(command))
  
  def apply(command: JsExp): JsCmd = 
  JsCmds.Run(funcId+"({'command': "+command.toJsCmd+", 'params': false});")
  
  def apply(command: String, params: JsExp) = 
  JsCmds.Run(funcId+"({'command': "+command.encJs+", 'params':"+
  params.toJsCmd+"});")
  
  def apply(command: String, target: String, params: JsExp) =
  JsCmds.Run(funcId+"({'command': "+command.encJs+", 'target': "+
  target.encJs+
  ", 'params':"+
  params.toJsCmd+"});")
  
  
  def apply(command: JsExp, params: JsExp) = 
  JsCmds.Run(funcId+"({'command': "+command.toJsCmd+", 'params':"+
  params.toJsCmd+"});")
  
  def apply(command: JsExp, target: JsExp, params: JsExp) =
  JsCmds.Run(funcId+"({'command': "+command.toJsCmd+", 'target': "+
  target.toJsCmd+
  ", 'params':"+
  params.toJsCmd+"});")
  
}

trait JsExp extends HtmlFixer {
  def toJsCmd: String
  
  def !(right: JsMethod): JsExp = new JsExp {
    def toJsCmd = JsExp.this.toJsCmd + "." + right.toJsCmd
  }
  
  def :=(right: JsExp): JsExp = new JsExp {
    def toJsCmd = JsExp.this.toJsCmd +" = " +right.toJsCmd
  }
  
  def cmd: JsCmd = JsCmds.Run(toJsCmd+";")
}

trait JsMethod {
  def toJsCmd: String
}

/**
* JavaScript Expressions
*/
object JE {
  implicit def strToS(in: String): Str = Str(in)
  implicit def boolToJsExp(in: Boolean): JsExp = if (in) JsTrue else JsFalse
  implicit def numToJsExp(in: Number): JsExp = Num(in)
  
  case class Num(n: Number) extends JsExp {
    def toJsCmd = n.toString
  }
  
  case class Stringify(in: JsExp) extends JsExp {
    def toJsCmd = "JSON.stringify("+in.toJsCmd+")"
  }
  
  case class JsArray[T <% JsExp](in: T*) extends JsExp {
    def toJsCmd = in.map(_.toJsCmd).mkString("[",", ","]")
  }
  
  case class ValById(id: String) extends JsExp {
    def toJsCmd = "document.getElementById("+id.encJs+").value"
  }
  
  /**
  * gets the element by ID
  */
  case class ElemById(id: String, then: String*) extends JsExp {
    override def toJsCmd = "document.getElementById("+id.encJs+")" + (
    if (then.isEmpty) "" else then.mkString(".", ".", "")
    )
  }
  
  
  
  /**
  * A String (JavaScript encoded)
  */
  case class Str(str: String) extends JsExp {
    def toJsCmd = str.encJs
  }
  
  /**
  * A JavaScript method that takes parameters
  */
  case class JsFunc(method: String, params: JsExp*) extends JsMethod {
    def toJsCmd = params.map(_.toJsCmd).mkString(method+"(", ", ", ")")
  }
  
  case class JsRaw(rawJsCmd: String) extends JsExp {
    def toJsCmd = rawJsCmd
  }
  
  case class JsVar(varName: String, andThen: String*) extends JsExp {
    def toJsCmd = varName + (if (andThen.isEmpty) "" 
    else andThen.mkString(".", ".", ""))
  }
  
  /**
  * A value that can be retrieved from an expression
  */
  case class JsVal(valueName: String) extends JsMethod {
    def toJsCmd = valueName
  }
  
  case object Value extends JsMethod {
    def toJsCmd = "value"
  }
  
  case object JsFalse extends JsExp {
    def toJsCmd = "false"
  }
  
  case object JsTrue extends JsExp {
    def toJsCmd = "true"
  }
  
  case object JqTabsSelected extends JsExp with JQueryRight {
    def toJsCmd = "tabsSelected()"
  }
  
  case object JqScrollToBottom extends JsExp with JQueryRight with JQueryLeft {
    def toJsCmd = "each(function(i) {this.scrollTop=this.scrollHeight;})"
  }
  
  object JqTabsClick {
    def apply(tab: JsExp): JsExp with JQueryRight with JQueryLeft = 
    new JsExp with JQueryRight with JQueryLeft {
      def toJsCmd = "tabsClick("+tab.toJsCmd+")"
    }
    
    def apply(tab: Int): JsExp with JQueryRight with JQueryLeft = 
    apply(Num(tab))
  }
  
  object JqTabs {
    def apply(in: JsExp): JsExp with JQueryRight with JQueryLeft = 
    new JsExp with JQueryRight with JQueryLeft {
      def toJsCmd = "tabs("+in.toJsCmd+")"
    }
    
    def apply(): JsExp with JQueryRight with JQueryLeft = 
    apply(JsRaw(""))
  }
  
  object AnonFunc {
    def apply(in: JsCmd): JsExp = new JsExp {
      def toJsCmd = "function() {"+in.toJsCmd+"}"
    }
    
    def apply(params: String, in: JsCmd): JsExp = new JsExp {
      def toJsCmd = "function("+params+") {"+in.toJsCmd+"}"
    }
  }
  

  
  object JsObj {
    def apply(members: (String, JsExp)*): JsExp = 
    new JsExp {
      def toJsCmd = members.
      map{case (n, v) => n.encJs+": "+v.toJsCmd}.
      mkString("{", ", ", "}")
    }
  }
  
  /**
  * A JQuery query
  */
  case class Jq(query: JsExp) extends JsExp with JQueryLeft {
    override def toJsCmd = "jQuery("+query.toJsCmd+")"
  }
  
  /**
  * A JQuery query for an element based on the id of the element
  */
  case class JqId(id: JsExp) extends JsExp with JQueryLeft {
    override def toJsCmd = "jQuery('#'+"+id.toJsCmd+")"
  }
  
  case class JqAttr(key: String, value: JsExp) extends JsExp with JQueryRight with JQueryLeft {
    def toJsCmd = "attr("+key.encJs+", "+value.toJsCmd+")"
  }
  
  /**
  * Append content to a JQuery
  */
  case class JqAppend(content: NodeSeq) extends JsExp with JQueryRight with JQueryLeft {
    override def toJsCmd = "append("+fixHtml("inline", content)+")"
  }
  
  /**
  * AppendTo content to a JQuery
  */
  case class JqAppendTo(content: NodeSeq) extends JsExp with JQueryRight with JQueryLeft {
    override def toJsCmd = "appendTo("+fixHtml("inline", content)+")"
  }
  
  /**
  * Append content to a JQuery
  */
  case class JqPrepend(content: NodeSeq) extends JsExp with JQueryRight with JQueryLeft {
    override def toJsCmd = "prepend("+fixHtml("inline", content)+")"
  }
  
  /**
  * Append content to a JQuery
  */
  case class JqPrependTo(content: NodeSeq) extends JsExp with JQueryRight with JQueryLeft {
    override def toJsCmd = "prependTo("+fixHtml("inline", content)+")"
  }
  
  object JqHtml {
    def apply() = new JsExp with JQueryRight {
      def toJsCmd = "html()"
    }
    
    def apply(content: NodeSeq) = new JsExp with JQueryRight with JQueryLeft {
      def toJsCmd = "html("+fixHtml("inline", content)+")"
    }
  }
  
  object JqText {
    def apply() = new JsExp with JQueryRight {
      def toJsCmd = "text()"
    }
    
    def apply(content: String) = new JsExp with JQueryRight with JQueryLeft {
      def toJsCmd = "text("+content.encJs+")"
    }
  }
  
  
  
  /**
  * Serialize the jquery into a JSON array
  */
  case object JsonSerialize extends JsExp with JQueryRight {
    def toJsCmd = "serializeArray()"
  }
}

trait JQueryRight {
  this: JsExp =>
  def toJsCmd: String
}

trait JQueryLeft {
  this: JsExp =>
  def >>(that: JQueryRight): JsExp = new JsExp {
    def toJsCmd = JQueryLeft.this.toJsCmd + "."+ that.toJsCmd
  }
  
  
  def >>(that: JQueryLeft with JQueryRight): JsExp with JQueryLeft =
  new JsExp with JQueryLeft {
    def toJsCmd = JQueryLeft.this.toJsCmd + "."+ that.toJsCmd
  }
}

trait HtmlFixer {
  def fixHtml(uid: String, content: NodeSeq): String = 
  AltXML.toXML(Group(S.session.map(s => s.fixHtml(s.processSurroundAndInclude("JS SetHTML id: "+uid, content))).openOr(content)),
  false, true).encJs
  
}

trait JsCmd extends HtmlFixer {
  def ++(other: JsCmd): JsCmd = JsCmds.CmdPair(this, other)  
  def toJsCmd: String
}

object JsCmds {
  implicit def seqJsToJs(in: Seq[JsCmd]): JsCmd = in.foldLeft[JsCmd](Noop)(_ ++ _)
  
  object Script {
    def apply(script: JsCmd): NodeSeq = <script>{Unparsed("""
      // <![CDATA[
      """+script.toJsCmd+"""
      // ]]>""")
    }
    </script>
  }
  
  /**
  * Makes the parameter the selected HTML element on load of the page
  *
  * @param in the element that should have focus
  *
  * @return the element and a script that will give the element focus
  */
  object FocusOnLoad {
    def apply(in: Elem): NodeSeq = {
      val (elem, id) = findOrAddId(in)
      elem ++ Script(OnLoad(Run("document.getElementById("+id.encJs+").focus();")))
    }
  }
  
  case class SetValById(id: String, right: JsExp) extends JsCmd {
    def toJsCmd = "document.getElementById("+id.encJs+").value = "+
    right.toJsCmd+";"
  }
  
  case class SetExp(left: JsExp, right: JsExp) extends JsCmd {
    def toJsCmd = left.toJsCmd + " = " + right.toJsCmd + ";"
  }
  
  case class SetElemById(id: String, right: JsExp, then: String*) extends JsCmd {
    def toJsCmd = "documenet.getElementById("+id.encJs+")"+ (
    if (then.isEmpty) "" else then.mkString(".", ".", "")
    ) + " = "+right.toJsCmd + ";"
  }
  
  implicit def jsExpToJsCmd(in: JsExp) = in.cmd
  
  
  case class OnLoad(cmd: JsCmd) extends JsCmd {
    def toJsCmd = "jQuery(document).ready(function() {"+cmd.toJsCmd+"});"
  }
  
  case class CmdPair(left: JsCmd, right: JsCmd) extends JsCmd {
    def toJsCmd = left.toJsCmd + "\n" + right.toJsCmd
  }
  
  object AppendHtml {
    def apply(uid: String, content: NodeSeq): JsCmd =
    JE.JqId(JE.Str(uid)) >> JE.JqAppend(content)
  }
  
  
  
  case class SetHtml(uid: String, content: NodeSeq) extends JsCmd {
    def toJsCmd = {
      val ret = "try{jQuery("+("#"+uid).encJs+").each(function(i) {this.innerHTML = "+fixHtml(uid, content)+";});} catch (e) {}"
      ret
    }
  }
  
  trait HasTime {
    def time: Can[Helpers.TimeSpan]
    def timeStr = time.map(_.len.toString) openOr ""
  }
  
  case class After(millis: Helpers.TimeSpan, toDo: JsCmd) extends JsCmd {
    def toJsCmd = "setTimeout(function() {"+toDo.toJsCmd+"}, "+millis.len+");"
  }
  
  object Show {
    def apply(uid: String) = new Show(uid, Empty)
    def apply(uid: String, time: Helpers.TimeSpan) = new Show(uid, Full(time))
  }
  
  class Show(val uid: String,val time: Can[Helpers.TimeSpan]) extends JsCmd with HasTime {
    def toJsCmd = "try{jQuery("+("#"+uid).encJs+").show("+timeStr+");} catch (e) {}"
  }
  
  object Hide {
    def apply(uid: String) = new Hide(uid, Empty)
    def apply(uid: String, time: Helpers.TimeSpan) = new Hide(uid, Full(time))    
  }
  
  class Hide(val uid: String,val time: Can[Helpers.TimeSpan]) extends JsCmd with HasTime {
    def toJsCmd = "try{jQuery("+("#"+uid).encJs+").hide("+timeStr+");} catch (e) {}"
  }
  
  case class Alert(text: String) extends JsCmd {
    def toJsCmd = "alert("+text.encJs+");"
  }
  
  object ModalDialog {
    def apply(html: NodeSeq) = new ModalDialog(html, Empty)
    def apply(html: NodeSeq, width: String) = new ModalDialog(html, Full(width))    
  }
  
  class ModalDialog(html: NodeSeq, width: Can[String]) extends JsCmd {
    def toJsCmd = "jQuery.blockUI("+AltXML.toXML(Group(S.session.map(s => 
    s.fixHtml(s.processSurroundAndInclude("Modal Dialog", html))).openOr(html)), false, true).encJs+
    (width.map(w => ", { width: '"+w+"' }").openOr("")) + ");"
  }
  
  case class Run(text: String) extends JsCmd {
    def toJsCmd = text
  }
  
  case object _Noop extends JsCmd {
    def toJsCmd = ""
  }
  
  implicit def cmdToString(in: JsCmd): String = in.toJsCmd
  
  val Noop: JsCmd = _Noop
  
  case object Unblock extends JsCmd {
    def toJsCmd = "jQuery.unblockUI();"
  }
  
  case class JsTry(what: JsCmd, alert: Boolean) extends JsCmd {
    def toJsCmd = "try { "+what.toJsCmd+" } catch (e) {"+(if (alert) "alert(e);" else "")+"}"
  }
  
  case class SetValueAndFocus(id: String, value: String) extends JsCmd {
    def toJsCmd = "jQuery('#"+id+"').attr('value', "+value.encJs+"); document.getElementById("+id.encJs+").focus();"
  }
  
  case class RedirectTo(where: String) extends JsCmd {
    private val context = S.contextPath
    def toJsCmd = "window.location = "+(context + where).encJs+";"
  }
  
  
  /**
  * Update a Select with new Options
  */
  case class ReplaceOptions(select: String, opts: List[(String, String)], dflt: Can[String]) extends JsCmd {
    def toJsCmd = """var x=document.getElementById("""+select.encJs+""");
    while (x.length > 0) {x.remove(0);}
    var y = null;
    """+
    opts.map{case (value, text) => "y=document.createElement('option'); "+
      "y.text = "+text.encJs+"; "+
      "y.value = "+value.encJs+"; "+
      (if (value == dflt) "y.selected = true; " else "") + "x.add(y, null); "
    }.mkString("\n")
  }
  
  case class DisplayMessage(where: String, msg: NodeSeq, duration: TimeSpan, fadeTime: TimeSpan) extends JsCmd {
    def realFadeTime: Long = fadeTime.len
    
    def realDuration: Long = duration.len
    
    def toJsCmd = (Show(where) ++ SetHtml(where, msg) ++ After(realDuration, Hide(where, realFadeTime))).toJsCmd
  }
}
