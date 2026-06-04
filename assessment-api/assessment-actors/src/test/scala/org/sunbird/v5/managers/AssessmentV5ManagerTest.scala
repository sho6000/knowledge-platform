package org.sunbird.v5.managers

import org.scalatest.{FlatSpec, Matchers}

import java.util
import scala.collection.JavaConverters._

class AssessmentV5ManagerTest extends FlatSpec with Matchers {

  // ─── helpers ───────────────────────────────────────────────────────────────

  private def mkMap(pairs: (String, AnyRef)*): util.Map[String, AnyRef] =
    new util.HashMap[String, AnyRef](pairs.toMap.asJava)

  private def mkList(items: util.Map[String, AnyRef]*): util.List[util.Map[String, AnyRef]] =
    new util.ArrayList[util.Map[String, AnyRef]](items.toList.asJava)

  private def option(value: String, label: AnyRef = null): util.Map[String, AnyRef] = {
    val m = mkMap("value" -> value)
    if (label != null) m.put("label", label)
    m
  }

  // ─── validateFTB ───────────────────────────────────────────────────────────

  "validateFTB" should "return empty list for valid FTB input" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "single",
      "type"        -> "string",
      "correctResponse" -> mkMap("value" -> "Paris")
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "text"))
    AssessmentV5Manager.validateFTB(rd, interactions) shouldBe empty
  }

  it should "report error when correctResponse.value is missing" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "single",
      "type"        -> "string",
      "correctResponse" -> mkMap()
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "text"))
    val errs = AssessmentV5Manager.validateFTB(rd, interactions)
    errs.exists(_.contains("correctResponse")) shouldBe true
  }

  it should "report error when cardinality is not single" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "multiple",
      "type"        -> "string",
      "correctResponse" -> mkMap("value" -> "Paris")
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "text"))
    val errs = AssessmentV5Manager.validateFTB(rd, interactions)
    errs.exists(_.contains("cardinality")) shouldBe true
  }

  it should "report error when interaction type is not text" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "single",
      "type"        -> "string",
      "correctResponse" -> mkMap("value" -> "Paris")
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "choice"))
    val errs = AssessmentV5Manager.validateFTB(rd, interactions)
    errs.exists(_.contains("interactions.response1.type")) shouldBe true
  }

  // ─── validateMTF ───────────────────────────────────────────────────────────

  "validateMTF" should "return empty list for valid MTF input" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("A" -> "1", "B" -> "2")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val leftOpts  = mkList(option("A"), option("B"))
    val rightOpts = mkList(option("1"), option("2"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> leftOpts, "right" -> rightOpts)
    ))
    AssessmentV5Manager.validateMTF(rd, interactions) shouldBe empty
  }

  it should "report error when left options are missing" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("A" -> "1")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val rightOpts = mkList(option("1"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> mkList(), "right" -> rightOpts)
    ))
    val errs = AssessmentV5Manager.validateMTF(rd, interactions)
    errs.exists(_.contains("left")) shouldBe true
  }

  it should "report error when RHS values are duplicated (one-to-one violation)" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("A" -> "1", "B" -> "1")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val leftOpts  = mkList(option("A"), option("B"))
    val rightOpts = mkList(option("1"), option("2"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> leftOpts, "right" -> rightOpts)
    ))
    val errs = AssessmentV5Manager.validateMTF(rd, interactions)
    errs.exists(_.contains("one-to-one")) shouldBe true
  }

  it should "report error when a correctResponse key is not in left options" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("X" -> "1")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val leftOpts  = mkList(option("A"))
    val rightOpts = mkList(option("1"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> leftOpts, "right" -> rightOpts)
    ))
    val errs = AssessmentV5Manager.validateMTF(rd, interactions)
    errs.exists(_.contains("not in left")) shouldBe true
  }

  // ─── validateOrdered ───────────────────────────────────────────────────────

  "validateOrdered" should "return empty list for valid SEQ/REO input" in {
    val correctArr: util.List[AnyRef] = new util.ArrayList[AnyRef](List("A", "B", "C").asJava)
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "ordered",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> correctArr)
    ))
    val opts = mkList(option("A"), option("B"), option("C"))
    val interactions = mkMap("response1" -> mkMap("type" -> "order", "options" -> opts))
    AssessmentV5Manager.validateOrdered(rd, interactions) shouldBe empty
  }

  it should "report error when cardinality is not ordered" in {
    val correctArr: util.List[AnyRef] = new util.ArrayList[AnyRef](List("A").asJava)
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> correctArr)
    ))
    val opts = mkList(option("A"))
    val interactions = mkMap("response1" -> mkMap("type" -> "order", "options" -> opts))
    val errs = AssessmentV5Manager.validateOrdered(rd, interactions)
    errs.exists(_.contains("cardinality")) shouldBe true
  }

  it should "report error when correctResponse length differs from options length" in {
    val correctArr: util.List[AnyRef] = new util.ArrayList[AnyRef](List("A", "B").asJava)
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "ordered",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> correctArr)
    ))
    val opts = mkList(option("A"), option("B"), option("C"))
    val interactions = mkMap("response1" -> mkMap("type" -> "order", "options" -> opts))
    val errs = AssessmentV5Manager.validateOrdered(rd, interactions)
    errs.exists(_.contains("length")) shouldBe true
  }

  // ─── filterByLanguage — top-level fields ──────────────────────────────────

  "filterByLanguage" should "return the value for the requested language" in {
    val metadata: util.Map[String, AnyRef] = mkMap(
      "body" -> mkMap("en" -> "Hello", "hi" -> "नमस्ते")
    )
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "नमस्ते"
  }

  it should "fall back to the system default language when requested lang is absent" in {
    val metadata: util.Map[String, AnyRef] = mkMap(
      "body" -> mkMap("en" -> "Hello")
    )
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "Hello"
  }

  it should "leave string body unchanged" in {
    val metadata: util.Map[String, AnyRef] = mkMap("body" -> "Hello")
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "Hello"
  }

  // ─── filterByLanguage — flat option labels ────────────────────────────────

  it should "resolve i18n label maps in flat options array" in {
    val labelMap: util.Map[String, AnyRef] = mkMap("en" -> "Apple", "hi" -> "सेब")
    val opt = option("A", labelMap)
    val opts = mkList(opt)
    val interactions: util.Map[String, AnyRef] = mkMap(
      "response1" -> mkMap("type" -> "order", "options" -> opts)
    )
    val metadata: util.Map[String, AnyRef] = mkMap("interactions" -> interactions)
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    opt.get("label") shouldBe "सेब"
  }

  it should "resolve i18n label maps in MTF left/right options" in {
    val labelEn: util.Map[String, AnyRef] = mkMap("en" -> "Capital", "hi" -> "राजधानी")
    val leftOpt  = option("A", labelEn)
    val rightOpt = option("1", mkMap("en" -> "Paris", "hi" -> "पेरिस").asInstanceOf[AnyRef])
    val sidesMap: util.Map[String, AnyRef] = mkMap(
      "left"  -> mkList(leftOpt),
      "right" -> mkList(rightOpt)
    )
    val interactions: util.Map[String, AnyRef] = mkMap(
      "response1" -> mkMap("type" -> "match", "options" -> sidesMap)
    )
    val metadata: util.Map[String, AnyRef] = mkMap("interactions" -> interactions)
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    leftOpt.get("label")  shouldBe "राजधानी"
    rightOpt.get("label") shouldBe "पेरिस"
  }

  // ─── processInteractions — v1.1 guard ─────────────────────────────────────

  "processInteractions" should "leave v1.1 interactions unchanged" in {
    val data: util.Map[String, AnyRef] = mkMap(
      "qumlVersion"  -> 1.1.asInstanceOf[AnyRef],
      "interactions" -> mkMap("response1" -> mkMap("type" -> "order", "options" -> mkList()))
    )
    val before = data.get("interactions")
    AssessmentV5Manager.processInteractions(data)
    data.get("interactions") shouldBe before
  }

  // ─── processResponseDeclaration — v1.1 guard ──────────────────────────────

  "processResponseDeclaration" should "not rename mapping fields for v1.1 data" in {
    val mappingEntry: util.Map[String, AnyRef] = mkMap("value" -> "A", "score" -> 1.0.asInstanceOf[AnyRef])
    val mappingList: util.List[util.Map[String, AnyRef]] = mkList(mappingEntry)
    val rdEntry: util.Map[String, AnyRef] = mkMap(
      "cardinality"     -> "ordered",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> new util.ArrayList[AnyRef]()),
      "mapping"         -> mappingList
    )
    val rd: util.Map[String, AnyRef] = mkMap("response1" -> rdEntry)
    val data: util.Map[String, AnyRef] = mkMap(
      "qumlVersion"         -> 1.1.asInstanceOf[AnyRef],
      "primaryCategory"     -> "Sequence Question",
      "responseDeclaration" -> rd
    )
    AssessmentV5Manager.processResponseDeclaration(data)
    val mapping = rdEntry.get("mapping").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    mapping.get(0).containsKey("value") shouldBe true
    mapping.get(0).containsKey("response") shouldBe false
  }

}
