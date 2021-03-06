import javax.xml.bind.DatatypeConverter._
import scala.reflect.io.File

// This test is self-modifying when run as follows:
//
//    (export V=v2.10.4
//     scalac-hash $V test/files/run/t8549.scala
//     scala-hash  $V -Doverwrite.source=test/files/run/t8549.scala Test
//    )
//
// Use this to re-establish a baseline for serialization compatibility.
object Test extends App {
  val overwrite: Option[File] = sys.props.get("overwrite.source").map(s => new File(new java.io.File(s)))

  def serialize(o: AnyRef): String = {
    val bos = new java.io.ByteArrayOutputStream()
    val out = new java.io.ObjectOutputStream(bos)
    out.writeObject(o)
    out.flush()
    printBase64Binary(bos.toByteArray())
  }

  def amend(file: File)(f: String => String): Unit = {
    file.writeAll(f(file.slurp))
  }
  def quote(s: String) = List("\"", s, "\"").mkString

  def patch(file: File, line: Int, prevResult: String, result: String): Unit = {
    amend(file) {
      content =>
        content.linesIterator.toList.zipWithIndex.map {
          case (content, i) if i == line - 1 =>
            val newContent = content.replace(quote(prevResult), quote(result))
            if (newContent != content)
              println(s"- $content\n+ $newContent\n")
            newContent
          case (content, _) => content
        }.mkString("\n")
    }
  }

  def updateComment(file: File): Unit = {
    val timestamp = {
      import java.text.SimpleDateFormat
      val sdf = new SimpleDateFormat("yyyyMMdd-HH:mm:ss")
      sdf.format(new java.util.Date)
    }
    val newComment = s"  // Generated on $timestamp with Scala ${scala.util.Properties.versionString})"
    amend(file) {
      content =>
        content.linesIterator.toList.map {
          f => f.replaceAll("""^ +// Generated on.*""", newComment)
        }.mkString("\n")
    }
  }

  def deserialize(string: String): AnyRef = {
    val bis = new java.io.ByteArrayInputStream(parseBase64Binary(string))
    val in = new java.io.ObjectInputStream(bis)
    in.readObject()
  }

  def checkRoundTrip[T <: AnyRef](instance: T)(f: T => AnyRef): Unit = {
    val result = serialize(instance)
    val reconstituted = deserialize(result).asInstanceOf[T]
    assert(f(instance) == f(reconstituted), (f(instance), f(reconstituted)))
  }

  def check[T <: AnyRef](instance: => T)(prevResult: String, f: T => AnyRef = (x: T) => x): Unit = {
    val result = serialize(instance)
    overwrite match {
      case Some(f) =>
        val lineNumberOfLiteralString = Thread.currentThread.getStackTrace.apply(2).getLineNumber
        patch(f, lineNumberOfLiteralString, prevResult, result)
      case None =>
        checkRoundTrip(instance)(f)
        assert(f(deserialize(prevResult).asInstanceOf[T]) == f(instance), s"$instance != f(deserialize(prevResult))")
        assert(prevResult == result, s"instance = $instance : ${instance.getClass}\n serialization unstable: ${prevResult}\n   found: ${result}")
    }
  }

  // Generated on 20141010-14:01:28 with Scala version 2.11.2)
  overwrite.foreach(updateComment)

  check(Some(1))("rO0ABXNyAApzY2FsYS5Tb21lESLyaV6hi3QCAAFMAAF4dAASTGphdmEvbGFuZy9PYmplY3Q7eHIADHNjYWxhLk9wdGlvbv5pN/3bDmZ0AgAAeHBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAQ==")
  check(None)("rO0ABXNyAAtzY2FsYS5Ob25lJEZQJPZTypSsAgAAeHIADHNjYWxhLk9wdGlvbv5pN/3bDmZ0AgAAeHA=")

  check(List(1, 2, 3))( "rO0ABXNyADJzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAABAwAAeHBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJzcQB+AAIAAAADc3IALHNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3RTZXJpYWxpemVFbmQkilxjW/dTC20CAAB4cHg=")
  check(Nil)(           "rO0ABXNyADJzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAABAwAAeHBzcgAsc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuTGlzdFNlcmlhbGl6ZUVuZCSKXGNb91MLbQIAAHhweA==")

  // TODO SI-8576 unstable under -Xcheckinit
  // check(Vector(1))(     "rO0ABXNyACFzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5WZWN0b3Lkd3dcHq6PXAIAC0kABWRlcHRoWgAFZGlydHlJAAhlbmRJbmRleEkABWZvY3VzSQAKc3RhcnRJbmRleFsACGRpc3BsYXkwdAATW0xqYXZhL2xhbmcvT2JqZWN0O1sACGRpc3BsYXkxcQB+AAFbAAhkaXNwbGF5MnEAfgABWwAIZGlzcGxheTNxAH4AAVsACGRpc3BsYXk0cQB+AAFbAAhkaXNwbGF5NXEAfgABeHAAAAABAAAAAAEAAAAAAAAAAHVyABNbTGphdmEubGFuZy5PYmplY3Q7kM5YnxBzKWwCAAB4cAAAACBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcA==")
  // check(Vector())(      "rO0ABXNyACFzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5WZWN0b3Lkd3dcHq6PXAIAC0kABWRlcHRoWgAFZGlydHlJAAhlbmRJbmRleEkABWZvY3VzSQAKc3RhcnRJbmRleFsACGRpc3BsYXkwdAATW0xqYXZhL2xhbmcvT2JqZWN0O1sACGRpc3BsYXkxcQB+AAFbAAhkaXNwbGF5MnEAfgABWwAIZGlzcGxheTNxAH4AAVsACGRpc3BsYXk0cQB+AAFbAAhkaXNwbGF5NXEAfgABeHAAAAAAAAAAAAAAAAAAAAAAAHBwcHBwcA==")

  import collection.{ mutable, immutable }

  class C
  check(reflect.classTag[C])("rO0ABXNyAB5zY2FsYS5yZWZsZWN0LkNsYXNzVGFnJCRhbm9uJDG7ePPrmQBkhgIAAUwAD3J1bnRpbWVDbGFzczEkMXQAEUxqYXZhL2xhbmcvQ2xhc3M7eHB2cgAGVGVzdCRDAAAAAAAAAAAAAAB4cA==")
  check(reflect.classTag[Int])("rO0ABXNyACVzY2FsYS5yZWZsZWN0Lk1hbmlmZXN0RmFjdG9yeSQkYW5vbiQ5zfmiSVNjtVICAAB4cgAcc2NhbGEucmVmbGVjdC5BbnlWYWxNYW5pZmVzdAAAAAAAAAABAgABTAAIdG9TdHJpbmd0ABJMamF2YS9sYW5nL1N0cmluZzt4cHQAA0ludA==")
  check(reflect.classTag[String])("rO0ABXNyAB5zY2FsYS5yZWZsZWN0LkNsYXNzVGFnJCRhbm9uJDG7ePPrmQBkhgIAAUwAD3J1bnRpbWVDbGFzczEkMXQAEUxqYXZhL2xhbmcvQ2xhc3M7eHB2cgAQamF2YS5sYW5nLlN0cmluZ6DwpDh6O7NCAgAAeHA=")
  check(reflect.classTag[Object])("rO0ABXNyACVzY2FsYS5yZWZsZWN0Lk1hbmlmZXN0RmFjdG9yeSQkYW5vbiQymPrtq/Ci1gsCAAB4cgAtc2NhbGEucmVmbGVjdC5NYW5pZmVzdEZhY3RvcnkkUGhhbnRvbU1hbmlmZXN0rzigP7KRh/kCAAFMAAh0b1N0cmluZ3QAEkxqYXZhL2xhbmcvU3RyaW5nO3hyAC9zY2FsYS5yZWZsZWN0Lk1hbmlmZXN0RmFjdG9yeSRDbGFzc1R5cGVNYW5pZmVzdFq6NWvfTgYFAgADTAAGcHJlZml4dAAOTHNjYWxhL09wdGlvbjtMAAxydW50aW1lQ2xhc3N0ABFMamF2YS9sYW5nL0NsYXNzO0wADXR5cGVBcmd1bWVudHN0ACFMc2NhbGEvY29sbGVjdGlvbi9pbW11dGFibGUvTGlzdDt4cHNyAAtzY2FsYS5Ob25lJEZQJPZTypSsAgAAeHIADHNjYWxhLk9wdGlvbv5pN/3bDmZ0AgAAeHB2cgAQamF2YS5sYW5nLk9iamVjdAAAAAAAAAAAAAAAeHBzcgAyc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuTGlzdCRTZXJpYWxpemF0aW9uUHJveHkAAAAAAAAAAQMAAHhwc3IALHNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3RTZXJpYWxpemVFbmQkilxjW/dTC20CAAB4cHh0AAZPYmplY3Q=")

  // TODO SI-8576 unstable under -Xcheckinit
  // check(Enum)(         "rO0ABXNyAApUZXN0JEVudW0ketCIyQ8C23MCAAJMAAJWMXQAGUxzY2FsYS9FbnVtZXJhdGlvbiRWYWx1ZTtMAAJWMnQAF0xzY2FsYS9FbnVtZXJhdGlvbiRWYWw7eHIAEXNjYWxhLkVudW1lcmF0aW9udaDN3ZgOWY4CAAhJAAZuZXh0SWRJABtzY2FsYSRFbnVtZXJhdGlvbiQkYm90dG9tSWRJABhzY2FsYSRFbnVtZXJhdGlvbiQkdG9wSWRMABRWYWx1ZU9yZGVyaW5nJG1vZHVsZXQAIkxzY2FsYS9FbnVtZXJhdGlvbiRWYWx1ZU9yZGVyaW5nJDtMAA9WYWx1ZVNldCRtb2R1bGV0AB1Mc2NhbGEvRW51bWVyYXRpb24kVmFsdWVTZXQkO0wACG5leHROYW1ldAAbTHNjYWxhL2NvbGxlY3Rpb24vSXRlcmF0b3I7TAAXc2NhbGEkRW51bWVyYXRpb24kJG5tYXB0AB5Mc2NhbGEvY29sbGVjdGlvbi9tdXRhYmxlL01hcDtMABdzY2FsYSRFbnVtZXJhdGlvbiQkdm1hcHEAfgAHeHAAAAArAAAAAAAAACtwcHBzcgAgc2NhbGEuY29sbGVjdGlvbi5tdXRhYmxlLkhhc2hNYXAAAAAAAAAAAQMAAHhwdw0AAALuAAAAAAAAAAQAeHNxAH4ACXcNAAAC7gAAAAEAAAAEAHNyABFqYXZhLmxhbmcuSW50ZWdlchLioKT3gYc4AgABSQAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAAqc3IAFXNjYWxhLkVudW1lcmF0aW9uJFZhbM9pZ6/J/O1PAgACSQAYc2NhbGEkRW51bWVyYXRpb24kVmFsJCRpTAAEbmFtZXQAEkxqYXZhL2xhbmcvU3RyaW5nO3hyABdzY2FsYS5FbnVtZXJhdGlvbiRWYWx1ZWJpfC/tIR1RAgACTAAGJG91dGVydAATTHNjYWxhL0VudW1lcmF0aW9uO0wAHHNjYWxhJEVudW1lcmF0aW9uJCRvdXRlckVudW1xAH4AEnhwcQB+AAhxAH4ACAAAACpweHNyABFUZXN0JEVudW0kJGFub24kMVlIjlmE1sXaAgAAeHEAfgARcQB+AAhxAH4ACHEAfgAT")
  // check(Enum.V1)(      "rO0ABXNyABFUZXN0JEVudW0kJGFub24kMVlIjlmE1sXaAgAAeHIAF3NjYWxhLkVudW1lcmF0aW9uJFZhbHVlYml8L+0hHVECAAJMAAYkb3V0ZXJ0ABNMc2NhbGEvRW51bWVyYXRpb247TAAcc2NhbGEkRW51bWVyYXRpb24kJG91dGVyRW51bXEAfgACeHBzcgAKVGVzdCRFbnVtJHrQiMkPAttzAgACTAACVjF0ABlMc2NhbGEvRW51bWVyYXRpb24kVmFsdWU7TAACVjJ0ABdMc2NhbGEvRW51bWVyYXRpb24kVmFsO3hyABFzY2FsYS5FbnVtZXJhdGlvbnWgzd2YDlmOAgAISQAGbmV4dElkSQAbc2NhbGEkRW51bWVyYXRpb24kJGJvdHRvbUlkSQAYc2NhbGEkRW51bWVyYXRpb24kJHRvcElkTAAUVmFsdWVPcmRlcmluZyRtb2R1bGV0ACJMc2NhbGEvRW51bWVyYXRpb24kVmFsdWVPcmRlcmluZyQ7TAAPVmFsdWVTZXQkbW9kdWxldAAdTHNjYWxhL0VudW1lcmF0aW9uJFZhbHVlU2V0JDtMAAhuZXh0TmFtZXQAG0xzY2FsYS9jb2xsZWN0aW9uL0l0ZXJhdG9yO0wAF3NjYWxhJEVudW1lcmF0aW9uJCRubWFwdAAeTHNjYWxhL2NvbGxlY3Rpb24vbXV0YWJsZS9NYXA7TAAXc2NhbGEkRW51bWVyYXRpb24kJHZtYXBxAH4AC3hwAAAAKwAAAAAAAAArcHBwc3IAIHNjYWxhLmNvbGxlY3Rpb24ubXV0YWJsZS5IYXNoTWFwAAAAAAAAAAEDAAB4cHcNAAAC7gAAAAAAAAAEAHhzcQB+AA13DQAAAu4AAAABAAAABABzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAKnNyABVzY2FsYS5FbnVtZXJhdGlvbiRWYWzPaWevyfztTwIAAkkAGHNjYWxhJEVudW1lcmF0aW9uJFZhbCQkaUwABG5hbWV0ABJMamF2YS9sYW5nL1N0cmluZzt4cQB+AAFxAH4ADHEAfgAMAAAAKnB4cQB+AANxAH4AFXEAfgAM")
  // check(Enum.V2)(      "rO0ABXNyABVzY2FsYS5FbnVtZXJhdGlvbiRWYWzPaWevyfztTwIAAkkAGHNjYWxhJEVudW1lcmF0aW9uJFZhbCQkaUwABG5hbWV0ABJMamF2YS9sYW5nL1N0cmluZzt4cgAXc2NhbGEuRW51bWVyYXRpb24kVmFsdWViaXwv7SEdUQIAAkwABiRvdXRlcnQAE0xzY2FsYS9FbnVtZXJhdGlvbjtMABxzY2FsYSRFbnVtZXJhdGlvbiQkb3V0ZXJFbnVtcQB+AAN4cHNyAApUZXN0JEVudW0ketCIyQ8C23MCAAJMAAJWMXQAGUxzY2FsYS9FbnVtZXJhdGlvbiRWYWx1ZTtMAAJWMnQAF0xzY2FsYS9FbnVtZXJhdGlvbiRWYWw7eHIAEXNjYWxhLkVudW1lcmF0aW9udaDN3ZgOWY4CAAhJAAZuZXh0SWRJABtzY2FsYSRFbnVtZXJhdGlvbiQkYm90dG9tSWRJABhzY2FsYSRFbnVtZXJhdGlvbiQkdG9wSWRMABRWYWx1ZU9yZGVyaW5nJG1vZHVsZXQAIkxzY2FsYS9FbnVtZXJhdGlvbiRWYWx1ZU9yZGVyaW5nJDtMAA9WYWx1ZVNldCRtb2R1bGV0AB1Mc2NhbGEvRW51bWVyYXRpb24kVmFsdWVTZXQkO0wACG5leHROYW1ldAAbTHNjYWxhL2NvbGxlY3Rpb24vSXRlcmF0b3I7TAAXc2NhbGEkRW51bWVyYXRpb24kJG5tYXB0AB5Mc2NhbGEvY29sbGVjdGlvbi9tdXRhYmxlL01hcDtMABdzY2FsYSRFbnVtZXJhdGlvbiQkdm1hcHEAfgAMeHAAAAArAAAAAAAAACtwcHBzcgAgc2NhbGEuY29sbGVjdGlvbi5tdXRhYmxlLkhhc2hNYXAAAAAAAAAAAQMAAHhwdw0AAALuAAAAAAAAAAQAeHNxAH4ADncNAAAC7gAAAAEAAAAEAHNyABFqYXZhLmxhbmcuSW50ZWdlchLioKT3gYc4AgABSQAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAAqcQB+AAR4c3IAEVRlc3QkRW51bSQkYW5vbiQxWUiOWYTWxdoCAAB4cQB+AAJxAH4ADXEAfgANcQB+AARxAH4ADQAAACpw")

  // IndexedSeqLike#Elements
  // TODO SI-8576 throws scala.UnitializedFieldError under -Xcheckinit
  // check(new immutable.Range(0, 1, 1).iterator)("rO0ABXNyAChzY2FsYS5jb2xsZWN0aW9uLkluZGV4ZWRTZXFMaWtlJEVsZW1lbnRzGF+1cBwmcx0CAANJAANlbmRJAAVpbmRleEwABiRvdXRlcnQAIUxzY2FsYS9jb2xsZWN0aW9uL0luZGV4ZWRTZXFMaWtlO3hwAAAAAQAAAABzcgAgc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuUmFuZ2Vpu6NUqxUyDQIAB0kAA2VuZFoAB2lzRW1wdHlJAAtsYXN0RWxlbWVudEkAEG51bVJhbmdlRWxlbWVudHNJAAVzdGFydEkABHN0ZXBJAA90ZXJtaW5hbEVsZW1lbnR4cAAAAAEAAAAAAAAAAAEAAAAAAAAAAQAAAAE="
  //   , _.toList)

  // check(new collection.concurrent.TrieMap[Any, Any]())( "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLmNvbmN1cnJlbnQuVHJpZU1hcKckxpgOIYHPAwAETAALZXF1YWxpdHlvYmp0ABJMc2NhbGEvbWF0aC9FcXVpdjtMAApoYXNoaW5nb2JqdAAcTHNjYWxhL3V0aWwvaGFzaGluZy9IYXNoaW5nO0wABHJvb3R0ABJMamF2YS9sYW5nL09iamVjdDtMAAtyb290dXBkYXRlcnQAOUxqYXZhL3V0aWwvY29uY3VycmVudC9hdG9taWMvQXRvbWljUmVmZXJlbmNlRmllbGRVcGRhdGVyO3hwc3IAMnNjYWxhLmNvbGxlY3Rpb24uY29uY3VycmVudC5UcmllTWFwJE1hbmdsZWRIYXNoaW5nhTBoJQ/mgb0CAAB4cHNyABhzY2FsYS5tYXRoLkVxdWl2JCRhbm9uJDLBbyx4dy/qGwIAAHhwc3IANHNjYWxhLmNvbGxlY3Rpb24uY29uY3VycmVudC5UcmllTWFwU2VyaWFsaXphdGlvbkVuZCSbjdgbbGCt2gIAAHhweA==")
  // not sure why this one needs stable serialization.

  // TODO SI-8576 unstable under -Xcheckinit
  check(collection.convert.Wrappers)( "rO0ABXNyACJzY2FsYS5jb2xsZWN0aW9uLmNvbnZlcnQuV3JhcHBlcnMkrrSziizavIECABJMABhEaWN0aW9uYXJ5V3JhcHBlciRtb2R1bGV0ADZMc2NhbGEvY29sbGVjdGlvbi9jb252ZXJ0L1dyYXBwZXJzJERpY3Rpb25hcnlXcmFwcGVyJDtMABZJdGVyYWJsZVdyYXBwZXIkbW9kdWxldAA0THNjYWxhL2NvbGxlY3Rpb24vY29udmVydC9XcmFwcGVycyRJdGVyYWJsZVdyYXBwZXIkO0wAFkl0ZXJhdG9yV3JhcHBlciRtb2R1bGV0ADRMc2NhbGEvY29sbGVjdGlvbi9jb252ZXJ0L1dyYXBwZXJzJEl0ZXJhdG9yV3JhcHBlciQ7TAAZSkNvbGxlY3Rpb25XcmFwcGVyJG1vZHVsZXQAN0xzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkSkNvbGxlY3Rpb25XcmFwcGVyJDtMABxKQ29uY3VycmVudE1hcFdyYXBwZXIkbW9kdWxldAA6THNjYWxhL2NvbGxlY3Rpb24vY29udmVydC9XcmFwcGVycyRKQ29uY3VycmVudE1hcFdyYXBwZXIkO0wAGUpEaWN0aW9uYXJ5V3JhcHBlciRtb2R1bGV0ADdMc2NhbGEvY29sbGVjdGlvbi9jb252ZXJ0L1dyYXBwZXJzJEpEaWN0aW9uYXJ5V3JhcHBlciQ7TAAaSkVudW1lcmF0aW9uV3JhcHBlciRtb2R1bGV0ADhMc2NhbGEvY29sbGVjdGlvbi9jb252ZXJ0L1dyYXBwZXJzJEpFbnVtZXJhdGlvbldyYXBwZXIkO0wAF0pJdGVyYWJsZVdyYXBwZXIkbW9kdWxldAA1THNjYWxhL2NvbGxlY3Rpb24vY29udmVydC9XcmFwcGVycyRKSXRlcmFibGVXcmFwcGVyJDtMABdKSXRlcmF0b3JXcmFwcGVyJG1vZHVsZXQANUxzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkSkl0ZXJhdG9yV3JhcHBlciQ7TAATSkxpc3RXcmFwcGVyJG1vZHVsZXQAMUxzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkSkxpc3RXcmFwcGVyJDtMABJKTWFwV3JhcHBlciRtb2R1bGV0ADBMc2NhbGEvY29sbGVjdGlvbi9jb252ZXJ0L1dyYXBwZXJzJEpNYXBXcmFwcGVyJDtMABlKUHJvcGVydGllc1dyYXBwZXIkbW9kdWxldAA3THNjYWxhL2NvbGxlY3Rpb24vY29udmVydC9XcmFwcGVycyRKUHJvcGVydGllc1dyYXBwZXIkO0wAEkpTZXRXcmFwcGVyJG1vZHVsZXQAMExzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkSlNldFdyYXBwZXIkO0wAG011dGFibGVCdWZmZXJXcmFwcGVyJG1vZHVsZXQAOUxzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkTXV0YWJsZUJ1ZmZlcldyYXBwZXIkO0wAGE11dGFibGVNYXBXcmFwcGVyJG1vZHVsZXQANkxzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkTXV0YWJsZU1hcFdyYXBwZXIkO0wAGE11dGFibGVTZXFXcmFwcGVyJG1vZHVsZXQANkxzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkTXV0YWJsZVNlcVdyYXBwZXIkO0wAGE11dGFibGVTZXRXcmFwcGVyJG1vZHVsZXQANkxzY2FsYS9jb2xsZWN0aW9uL2NvbnZlcnQvV3JhcHBlcnMkTXV0YWJsZVNldFdyYXBwZXIkO0wAEVNlcVdyYXBwZXIkbW9kdWxldAAvTHNjYWxhL2NvbGxlY3Rpb24vY29udmVydC9XcmFwcGVycyRTZXFXcmFwcGVyJDt4cHBwcHBwcHBwcHBwcHBwcHBwcA==")

  check(immutable.BitSet(1, 2, 3))( "rO0ABXNyAClzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5CaXRTZXQkQml0U2V0MR9dg8JGRI8UAgABSgAFZWxlbXN4cgAhc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuQml0U2V0Flz5Ms3qxsoCAAB4cAAAAAAAAAAO")
  check(immutable.HashMap())( "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoTWFwJFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAAB4")
  check(immutable.HashMap(1 -> 2))( "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoTWFwJFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAAFzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJ4")
  check(immutable.HashMap(1 -> 2, 3 -> 4))( "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoTWFwJFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAAJzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJzcQB+AAIAAAADc3EAfgACAAAABHg=")
  // TODO provoke HashMapCollision1

  check(immutable.HashSet())(        "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoU2V0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAAB4")
  check(immutable.HashSet(1))(       "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoU2V0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAAFzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXg=")
  check(immutable.HashSet(1, 2))(    "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoU2V0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAAJzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJ4")
  check(immutable.HashSet(1, 2, 3))( "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoU2V0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAANzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJzcQB+AAIAAAADeA==")
  // TODO provoke HashSetCollision1

  check(immutable.ListMap())(       "rO0ABXNyADBzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0TWFwJEVtcHR5TGlzdE1hcCSNalsvpBZeDgIAAHhyACJzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0TWFwBC1gfIkUSKsCAAB4cA==")
  check(immutable.ListMap(1 -> 2))( "rO0ABXNyACdzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0TWFwJE5vZGWmciM1Yav+8gIAA0wABiRvdXRlcnQAJExzY2FsYS9jb2xsZWN0aW9uL2ltbXV0YWJsZS9MaXN0TWFwO0wAA2tleXQAEkxqYXZhL2xhbmcvT2JqZWN0O0wABXZhbHVlcQB+AAJ4cgAic2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuTGlzdE1hcAQtYHyJFEirAgAAeHBzcgAwc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuTGlzdE1hcCRFbXB0eUxpc3RNYXAkjWpbL6QWXg4CAAB4cQB+AANzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4ABwAAAAI=")
  check(immutable.Queue())(         "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5RdWV1ZZY146W3qSuhAgACTAACaW50ACFMc2NhbGEvY29sbGVjdGlvbi9pbW11dGFibGUvTGlzdDtMAANvdXRxAH4AAXhwc3IAMnNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3QkU2VyaWFsaXphdGlvblByb3h5AAAAAAAAAAEDAAB4cHNyACxzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0U2VyaWFsaXplRW5kJIpcY1v3UwttAgAAeHB4cQB+AAQ=")
  check(immutable.Queue(1, 2, 3))(  "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5RdWV1ZZY146W3qSuhAgACTAACaW50ACFMc2NhbGEvY29sbGVjdGlvbi9pbW11dGFibGUvTGlzdDtMAANvdXRxAH4AAXhwc3IAMnNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3QkU2VyaWFsaXphdGlvblByb3h5AAAAAAAAAAEDAAB4cHNyACxzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0U2VyaWFsaXplRW5kJIpcY1v3UwttAgAAeHB4c3EAfgADc3IAEWphdmEubGFuZy5JbnRlZ2VyEuKgpPeBhzgCAAFJAAV2YWx1ZXhyABBqYXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cAAAAAFzcQB+AAgAAAACc3EAfgAIAAAAA3EAfgAGeA==")

  // TODO SI-8576 throws scala.UnitializedFieldError under -Xcheckinit
  // check(new immutable.Range(0, 1, 1))( "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5SYW5nZWm7o1SrFTINAgAHSQADZW5kWgAHaXNFbXB0eUkAC2xhc3RFbGVtZW50SQAQbnVtUmFuZ2VFbGVtZW50c0kABXN0YXJ0SQAEc3RlcEkAD3Rlcm1pbmFsRWxlbWVudHhwAAAAAQAAAAAAAAAAAQAAAAAAAAABAAAAAQ==")

  check(immutable.Set())(              "rO0ABXNyAChzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TZXQkRW1wdHlTZXQk8Hk3TFN0uDYCAAB4cA==")
  check(immutable.Set(1))(             "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TZXQkU2V0MREd3c4yqtWTAgABTAAFZWxlbTF0ABJMamF2YS9sYW5nL09iamVjdDt4cHNyABFqYXZhLmxhbmcuSW50ZWdlchLioKT3gYc4AgABSQAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAAB")
  check(immutable.Set(1, 2))(          "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TZXQkU2V0MqaV02sZQzV0AgACTAAFZWxlbTF0ABJMamF2YS9sYW5nL09iamVjdDtMAAVlbGVtMnEAfgABeHBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAwAAAAI=")
  check(immutable.Set(1, 2, 3))(       "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TZXQkU2V0M84syT0560SgAgADTAAFZWxlbTF0ABJMamF2YS9sYW5nL09iamVjdDtMAAVlbGVtMnEAfgABTAAFZWxlbTNxAH4AAXhwc3IAEWphdmEubGFuZy5JbnRlZ2VyEuKgpPeBhzgCAAFJAAV2YWx1ZXhyABBqYXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cAAAAAFzcQB+AAMAAAACc3EAfgADAAAAAw==")
  check(immutable.Set(1, 2, 3, 4))(    "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TZXQkU2V0NM26psRRbei1AgAETAAFZWxlbTF0ABJMamF2YS9sYW5nL09iamVjdDtMAAVlbGVtMnEAfgABTAAFZWxlbTNxAH4AAUwABWVsZW00cQB+AAF4cHNyABFqYXZhLmxhbmcuSW50ZWdlchLioKT3gYc4AgABSQAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAABc3EAfgADAAAAAnNxAH4AAwAAAANzcQB+AAMAAAAE")
  check(immutable.Set(1, 2, 3, 4, 5))( "rO0ABXNyADVzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5IYXNoU2V0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAACAwAAeHB3BAAAAAVzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAABXNxAH4AAgAAAAFzcQB+AAIAAAACc3EAfgACAAAAA3NxAH4AAgAAAAR4")

  check(immutable.Stack(1, 2, 3))( "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TdGFjaxtt3qEbMvq+AgABTAAFZWxlbXN0ACFMc2NhbGEvY29sbGVjdGlvbi9pbW11dGFibGUvTGlzdDt4cHNyADJzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAABAwAAeHBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4ABQAAAAJzcQB+AAUAAAADc3IALHNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3RTZXJpYWxpemVFbmQkilxjW/dTC20CAAB4cHg=")

  // TODO SI-8576 Uninitialized field: IndexedSeqLike.scala: 56
  // check(immutable.Stream(1, 2, 3))( "rO0ABXNyACZzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TdHJlYW0kQ29uc/ekjBXM3TlFAgADTAACaGR0ABJMamF2YS9sYW5nL09iamVjdDtMAAV0bEdlbnQAEUxzY2FsYS9GdW5jdGlvbjA7TAAFdGxWYWx0ACNMc2NhbGEvY29sbGVjdGlvbi9pbW11dGFibGUvU3RyZWFtO3hyACFzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5TdHJlYW0552RDntM42gIAAHhwc3IAEWphdmEubGFuZy5JbnRlZ2VyEuKgpPeBhzgCAAFJAAV2YWx1ZXhyABBqYXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cAAAAAFzcgAtc2NhbGEuY29sbGVjdGlvbi5JdGVyYXRvciQkYW5vbmZ1biR0b1N0cmVhbSQxRWR4We0SX0UCAAFMAAYkb3V0ZXJ0ABtMc2NhbGEvY29sbGVjdGlvbi9JdGVyYXRvcjt4cHNyAChzY2FsYS5jb2xsZWN0aW9uLkluZGV4ZWRTZXFMaWtlJEVsZW1lbnRzGF+1cBwmcx0CAANJAANlbmRJAAVpbmRleEwABiRvdXRlcnQAIUxzY2FsYS9jb2xsZWN0aW9uL0luZGV4ZWRTZXFMaWtlO3hwAAAAAwAAAAFzcgArc2NhbGEuY29sbGVjdGlvbi5tdXRhYmxlLldyYXBwZWRBcnJheSRvZkludMmRLBcI15VjAgABWwAFYXJyYXl0AAJbSXhwdXIAAltJTbpgJnbqsqUCAAB4cAAAAAMAAAABAAAAAgAAAANw")

  check(immutable.TreeSet[Int]())(   "rO0ABXNyACJzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5UcmVlU2V0sRdVIDjbWAsCAAJMAAhvcmRlcmluZ3QAFUxzY2FsYS9tYXRoL09yZGVyaW5nO0wABHRyZWV0AC5Mc2NhbGEvY29sbGVjdGlvbi9pbW11dGFibGUvUmVkQmxhY2tUcmVlJFRyZWU7eHBzcgAYc2NhbGEubWF0aC5PcmRlcmluZyRJbnQkC4BMdr1Z51wCAAB4cHA=")

  // TODO SI-8576 unstable under -Xcheckinit
  // check(immutable.TreeSet(1, 2, 3))( "rO0ABXNyACJzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5UcmVlU2V0sRdVIDjbWAsCAAJMAAhvcmRlcmluZ3QAFUxzY2FsYS9tYXRoL09yZGVyaW5nO0wABHRyZWV0AC5Mc2NhbGEvY29sbGVjdGlvbi9pbW11dGFibGUvUmVkQmxhY2tUcmVlJFRyZWU7eHBzcgAYc2NhbGEubWF0aC5PcmRlcmluZyRJbnQkC4BMdr1Z51wCAAB4cHNyADFzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5SZWRCbGFja1RyZWUkQmxhY2tUcmVlzRxnCKenVAECAAB4cgAsc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuUmVkQmxhY2tUcmVlJFRyZWVrqCSyHJbsMgIABUkABWNvdW50TAADa2V5dAASTGphdmEvbGFuZy9PYmplY3Q7TAAEbGVmdHEAfgACTAAFcmlnaHRxAH4AAkwABXZhbHVlcQB+AAh4cAAAAANzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAnNxAH4ABgAAAAFzcQB+AAoAAAABcHBzcgAXc2NhbGEucnVudGltZS5Cb3hlZFVuaXR0pn1HHezLmgIAAHhwc3EAfgAGAAAAAXNxAH4ACgAAAANwcHEAfgAQcQB+ABA=")

  // TODO SI-8576 Uninitialized field under -Xcheckinit
  // check(mutable.ArrayBuffer(1, 2, 3))(      "rO0ABXNyACRzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuQXJyYXlCdWZmZXIVOLBTg4KOcwIAA0kAC2luaXRpYWxTaXplSQAFc2l6ZTBbAAVhcnJheXQAE1tMamF2YS9sYW5nL09iamVjdDt4cAAAABAAAAADdXIAE1tMamF2YS5sYW5nLk9iamVjdDuQzlifEHMpbAIAAHhwAAAAEHNyABFqYXZhLmxhbmcuSW50ZWdlchLioKT3gYc4AgABSQAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAABc3EAfgAFAAAAAnNxAH4ABQAAAANwcHBwcHBwcHBwcHBw")
  // TODO SI-8576 Uninitialized field under -Xcheckinit
  // check(mutable.ArraySeq(1, 2, 3))(         "rO0ABXNyACFzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuQXJyYXlTZXEVPD3SKEkOcwIAAkkABmxlbmd0aFsABWFycmF5dAATW0xqYXZhL2xhbmcvT2JqZWN0O3hwAAAAA3VyABNbTGphdmEubGFuZy5PYmplY3Q7kM5YnxBzKWwCAAB4cAAAAANzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4ABQAAAAJzcQB+AAUAAAAD")
  check(mutable.ArrayStack(1, 2, 3))(       "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuQXJyYXlTdGFja3bdxXbcnLBeAgACSQAqc2NhbGEkY29sbGVjdGlvbiRtdXRhYmxlJEFycmF5U3RhY2skJGluZGV4WwAqc2NhbGEkY29sbGVjdGlvbiRtdXRhYmxlJEFycmF5U3RhY2skJHRhYmxldAATW0xqYXZhL2xhbmcvT2JqZWN0O3hwAAAAA3VyABNbTGphdmEubGFuZy5PYmplY3Q7kM5YnxBzKWwCAAB4cAAAAANzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAA3NxAH4ABQAAAAJzcQB+AAUAAAAB")
  check(mutable.DoubleLinkedList(1, 2, 3))( "rO0ABXNyAClzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuRG91YmxlTGlua2VkTGlzdI73LKsKRr1RAgADTAAEZWxlbXQAEkxqYXZhL2xhbmcvT2JqZWN0O0wABG5leHR0AB5Mc2NhbGEvY29sbGVjdGlvbi9tdXRhYmxlL1NlcTtMAARwcmV2cQB+AAJ4cHNyABFqYXZhLmxhbmcuSW50ZWdlchLioKT3gYc4AgABSQAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAABc3EAfgAAc3EAfgAEAAAAAnNxAH4AAHNxAH4ABAAAAANzcQB+AABwcQB+AAtxAH4ACXEAfgAHcQB+AANw")

  check(mutable.HashMap())( "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuSGFzaE1hcAAAAAAAAAABAwAAeHB3DQAAAu4AAAAAAAAABAB4")
  check(mutable.HashMap(1 -> 1))( "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuSGFzaE1hcAAAAAAAAAABAwAAeHB3DQAAAu4AAAABAAAABABzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXEAfgAEeA==")
  check(mutable.HashSet(1, 2, 3))( "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuSGFzaFNldAAAAAAAAAABAwAAeHB3DQAAAcIAAAADAAAABQBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJzcQB+AAIAAAADeA==")
  // TODO SI-8576 Uninitialized field under -Xcheckinit
  // check(new mutable.History())( "rO0ABXNyACBzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuSGlzdG9yeUhuXxDIFJrsAgACSQAKbWF4SGlzdG9yeUwAA2xvZ3QAIExzY2FsYS9jb2xsZWN0aW9uL211dGFibGUvUXVldWU7eHAAAAPoc3IAHnNjYWxhLmNvbGxlY3Rpb24ubXV0YWJsZS5RdWV1ZbjMURVfOuHHAgAAeHIAJHNjYWxhLmNvbGxlY3Rpb24ubXV0YWJsZS5NdXRhYmxlTGlzdFJpnjJ+gFbAAgADSQADbGVuTAAGZmlyc3QwdAAlTHNjYWxhL2NvbGxlY3Rpb24vbXV0YWJsZS9MaW5rZWRMaXN0O0wABWxhc3QwcQB+AAV4cAAAAABzcgAjc2NhbGEuY29sbGVjdGlvbi5tdXRhYmxlLkxpbmtlZExpc3Sak+nGCZHaUQIAAkwABGVsZW10ABJMamF2YS9sYW5nL09iamVjdDtMAARuZXh0dAAeTHNjYWxhL2NvbGxlY3Rpb24vbXV0YWJsZS9TZXE7eHBwcQB+AApxAH4ACg==")
  check(mutable.LinkedHashMap(1 -> 2))( "rO0ABXNyACZzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuTGlua2VkSGFzaE1hcAAAAAAAAAABAwAAeHB3DQAAAu4AAAABAAAABABzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJ4")
  check(mutable.LinkedHashSet(1, 2, 3))( "rO0ABXNyACZzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuTGlua2VkSGFzaFNldAAAAAAAAAABAwAAeHB3DQAAAu4AAAADAAAABABzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4AAgAAAAJzcQB+AAIAAAADeA==")
  check(mutable.LinkedList(1, 2, 3))( "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuTGlua2VkTGlzdJqT6cYJkdpRAgACTAAEZWxlbXQAEkxqYXZhL2xhbmcvT2JqZWN0O0wABG5leHR0AB5Mc2NhbGEvY29sbGVjdGlvbi9tdXRhYmxlL1NlcTt4cHNyABFqYXZhLmxhbmcuSW50ZWdlchLioKT3gYc4AgABSQAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAABc3EAfgAAc3EAfgAEAAAAAnNxAH4AAHNxAH4ABAAAAANzcQB+AABwcQB+AAs=")

  // TODO SI-8576 unstable under -Xcheckinit
  // check(mutable.ListBuffer(1, 2, 3))( "rO0ABXNyACNzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuTGlzdEJ1ZmZlci9y9I7QyWzGAwAEWgAIZXhwb3J0ZWRJAANsZW5MAAVsYXN0MHQAKUxzY2FsYS9jb2xsZWN0aW9uL2ltbXV0YWJsZS8kY29sb24kY29sb247TAAqc2NhbGEkY29sbGVjdGlvbiRtdXRhYmxlJExpc3RCdWZmZXIkJHN0YXJ0dAAhTHNjYWxhL2NvbGxlY3Rpb24vaW1tdXRhYmxlL0xpc3Q7eHBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4ABAAAAAJzcQB+AAQAAAADc3IALHNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3RTZXJpYWxpemVFbmQkilxjW/dTC20CAAB4cHcFAAAAAAN4")
  check(new mutable.StringBuilder(new java.lang.StringBuilder("123")))( "rO0ABXNyACZzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuU3RyaW5nQnVpbGRlcomvqgGv1tTxAgABTAAKdW5kZXJseWluZ3QAGUxqYXZhL2xhbmcvU3RyaW5nQnVpbGRlcjt4cHNyABdqYXZhLmxhbmcuU3RyaW5nQnVpbGRlcjzV+xRaTGrLAwAAeHB3BAAAAAN1cgACW0OwJmaw4l2ErAIAAHhwAAAAEwAxADIAMwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAeA==")
  check(mutable.UnrolledBuffer[Int]())( "rO0ABXNyACdzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuVW5yb2xsZWRCdWZmZXIAAAAAAAAAAQMAAUwAA3RhZ3QAGExzY2FsYS9yZWZsZWN0L0NsYXNzVGFnO3hwc3IAJXNjYWxhLnJlZmxlY3QuTWFuaWZlc3RGYWN0b3J5JCRhbm9uJDnN+aJJU2O1UgIAAHhyABxzY2FsYS5yZWZsZWN0LkFueVZhbE1hbmlmZXN0AAAAAAAAAAECAAFMAAh0b1N0cmluZ3QAEkxqYXZhL2xhbmcvU3RyaW5nO3hwdAADSW50dwQAAAAAeA==")

  import collection.parallel
  check(parallel.immutable.ParHashMap(1 -> 2))( "rO0ABXNyAC5zY2FsYS5jb2xsZWN0aW9uLnBhcmFsbGVsLmltbXV0YWJsZS5QYXJIYXNoTWFwAAAAAAAAAAECAANMAA9TY2FuTGVhZiRtb2R1bGV0ADVMc2NhbGEvY29sbGVjdGlvbi9wYXJhbGxlbC9QYXJJdGVyYWJsZUxpa2UkU2NhbkxlYWYkO0wAD1NjYW5Ob2RlJG1vZHVsZXQANUxzY2FsYS9jb2xsZWN0aW9uL3BhcmFsbGVsL1Bhckl0ZXJhYmxlTGlrZSRTY2FuTm9kZSQ7TAAEdHJpZXQAJExzY2FsYS9jb2xsZWN0aW9uL2ltbXV0YWJsZS9IYXNoTWFwO3hwcHBzcgA1c2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuSGFzaE1hcCRTZXJpYWxpemF0aW9uUHJveHkAAAAAAAAAAgMAAHhwdwQAAAABc3IAEWphdmEubGFuZy5JbnRlZ2VyEuKgpPeBhzgCAAFJAAV2YWx1ZXhyABBqYXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cAAAAAFzcQB+AAcAAAACeA==")
  check(parallel.immutable.ParHashSet(1, 2, 3))( "rO0ABXNyAC5zY2FsYS5jb2xsZWN0aW9uLnBhcmFsbGVsLmltbXV0YWJsZS5QYXJIYXNoU2V0AAAAAAAAAAECAANMAA9TY2FuTGVhZiRtb2R1bGV0ADVMc2NhbGEvY29sbGVjdGlvbi9wYXJhbGxlbC9QYXJJdGVyYWJsZUxpa2UkU2NhbkxlYWYkO0wAD1NjYW5Ob2RlJG1vZHVsZXQANUxzY2FsYS9jb2xsZWN0aW9uL3BhcmFsbGVsL1Bhckl0ZXJhYmxlTGlrZSRTY2FuTm9kZSQ7TAAEdHJpZXQAJExzY2FsYS9jb2xsZWN0aW9uL2ltbXV0YWJsZS9IYXNoU2V0O3hwcHBzcgA1c2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuSGFzaFNldCRTZXJpYWxpemF0aW9uUHJveHkAAAAAAAAAAgMAAHhwdwQAAAADc3IAEWphdmEubGFuZy5JbnRlZ2VyEuKgpPeBhzgCAAFJAAV2YWx1ZXhyABBqYXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cAAAAAFzcQB+AAcAAAACc3EAfgAHAAAAA3g=")
  // TODO SI-8576 Uninitialized field under -Xcheckinit
  // check(new parallel.immutable.ParRange(new Range(0, 1, 2)))( "rO0ABXNyACxzY2FsYS5jb2xsZWN0aW9uLnBhcmFsbGVsLmltbXV0YWJsZS5QYXJSYW5nZQAAAAAAAAABAgAETAAXUGFyUmFuZ2VJdGVyYXRvciRtb2R1bGV0AEBMc2NhbGEvY29sbGVjdGlvbi9wYXJhbGxlbC9pbW11dGFibGUvUGFyUmFuZ2UkUGFyUmFuZ2VJdGVyYXRvciQ7TAAPU2NhbkxlYWYkbW9kdWxldAA1THNjYWxhL2NvbGxlY3Rpb24vcGFyYWxsZWwvUGFySXRlcmFibGVMaWtlJFNjYW5MZWFmJDtMAA9TY2FuTm9kZSRtb2R1bGV0ADVMc2NhbGEvY29sbGVjdGlvbi9wYXJhbGxlbC9QYXJJdGVyYWJsZUxpa2UkU2Nhbk5vZGUkO0wABXJhbmdldAAiTHNjYWxhL2NvbGxlY3Rpb24vaW1tdXRhYmxlL1JhbmdlO3hwcHBwc3IAIHNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLlJhbmdlabujVKsVMg0CAAdJAANlbmRaAAdpc0VtcHR5SQALbGFzdEVsZW1lbnRJABBudW1SYW5nZUVsZW1lbnRzSQAFc3RhcnRJAARzdGVwSQAPdGVybWluYWxFbGVtZW50eHAAAAABAAAAAAAAAAABAAAAAAAAAAIAAAAC")
  // TODO SI-8576 unstable under -Xcheckinit
  // check(parallel.mutable.ParArray(1, 2, 3))( "rO0ABXNyACpzY2FsYS5jb2xsZWN0aW9uLnBhcmFsbGVsLm11dGFibGUuUGFyQXJyYXkAAAAAAAAAAQMABEwAF1BhckFycmF5SXRlcmF0b3IkbW9kdWxldAA+THNjYWxhL2NvbGxlY3Rpb24vcGFyYWxsZWwvbXV0YWJsZS9QYXJBcnJheSRQYXJBcnJheUl0ZXJhdG9yJDtMAA9TY2FuTGVhZiRtb2R1bGV0ADVMc2NhbGEvY29sbGVjdGlvbi9wYXJhbGxlbC9QYXJJdGVyYWJsZUxpa2UkU2NhbkxlYWYkO0wAD1NjYW5Ob2RlJG1vZHVsZXQANUxzY2FsYS9jb2xsZWN0aW9uL3BhcmFsbGVsL1Bhckl0ZXJhYmxlTGlrZSRTY2FuTm9kZSQ7TAAIYXJyYXlzZXF0ACNMc2NhbGEvY29sbGVjdGlvbi9tdXRhYmxlL0FycmF5U2VxO3hwcHBwc3IAMXNjYWxhLmNvbGxlY3Rpb24ucGFyYWxsZWwubXV0YWJsZS5FeHBvc2VkQXJyYXlTZXGx2OTefAodSQIAAkkABmxlbmd0aFsABWFycmF5dAATW0xqYXZhL2xhbmcvT2JqZWN0O3hyACFzY2FsYS5jb2xsZWN0aW9uLm11dGFibGUuQXJyYXlTZXEVPD3SKEkOcwIAAkkABmxlbmd0aFsABWFycmF5cQB+AAd4cAAAAAN1cgATW0xqYXZhLmxhbmcuT2JqZWN0O5DOWJ8QcylsAgAAeHAAAAADcHBwAAAAA3VxAH4ACgAAABBzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4ADQAAAAJzcQB+AA0AAAADcHBwcHBwcHBwcHBwcHg=")
  check(parallel.mutable.ParHashMap(1 -> 2))( "rO0ABXNyACxzY2FsYS5jb2xsZWN0aW9uLnBhcmFsbGVsLm11dGFibGUuUGFySGFzaE1hcAAAAAAAAAABAwACTAAPU2NhbkxlYWYkbW9kdWxldAA1THNjYWxhL2NvbGxlY3Rpb24vcGFyYWxsZWwvUGFySXRlcmFibGVMaWtlJFNjYW5MZWFmJDtMAA9TY2FuTm9kZSRtb2R1bGV0ADVMc2NhbGEvY29sbGVjdGlvbi9wYXJhbGxlbC9QYXJJdGVyYWJsZUxpa2UkU2Nhbk5vZGUkO3hwcHB3DQAAAu4AAAABAAAABAFzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4ABAAAAAJ4")
  check(parallel.mutable.ParHashSet(1, 2, 3))( "rO0ABXNyACxzY2FsYS5jb2xsZWN0aW9uLnBhcmFsbGVsLm11dGFibGUuUGFySGFzaFNldAAAAAAAAAABAwACTAAPU2NhbkxlYWYkbW9kdWxldAA1THNjYWxhL2NvbGxlY3Rpb24vcGFyYWxsZWwvUGFySXRlcmFibGVMaWtlJFNjYW5MZWFmJDtMAA9TY2FuTm9kZSRtb2R1bGV0ADVMc2NhbGEvY29sbGVjdGlvbi9wYXJhbGxlbC9QYXJJdGVyYWJsZUxpa2UkU2Nhbk5vZGUkO3hwcHB3DQAAAcIAAAADAAAAGwFzcgARamF2YS5sYW5nLkludGVnZXIS4qCk94GHOAIAAUkABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAXNxAH4ABAAAAAJzcQB+AAQAAAADeA==")

  check("...".r)("rO0ABXNyABlzY2FsYS51dGlsLm1hdGNoaW5nLlJlZ2V44u3Vap7wIb8CAAJMAAdwYXR0ZXJudAAZTGphdmEvdXRpbC9yZWdleC9QYXR0ZXJuO0wAJXNjYWxhJHV0aWwkbWF0Y2hpbmckUmVnZXgkJGdyb3VwTmFtZXN0ABZMc2NhbGEvY29sbGVjdGlvbi9TZXE7eHBzcgAXamF2YS51dGlsLnJlZ2V4LlBhdHRlcm5GZ9VrbkkCDQIAAkkABWZsYWdzTAAHcGF0dGVybnQAEkxqYXZhL2xhbmcvU3RyaW5nO3hwAAAAAHQAAy4uLnNyADJzY2FsYS5jb2xsZWN0aW9uLmltbXV0YWJsZS5MaXN0JFNlcmlhbGl6YXRpb25Qcm94eQAAAAAAAAABAwAAeHBzcgAsc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuTGlzdFNlcmlhbGl6ZUVuZCSKXGNb91MLbQIAAHhweA==",
                 r => (r.toString))
}
