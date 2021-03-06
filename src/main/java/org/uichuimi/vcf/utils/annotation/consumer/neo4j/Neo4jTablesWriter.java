package org.uichuimi.vcf.utils.annotation.consumer.neo4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.uichuimi.vcf.header.VcfHeader;
import org.uichuimi.vcf.utils.annotation.AnnotationConstants;
import org.uichuimi.vcf.utils.annotation.Genotype;
import org.uichuimi.vcf.utils.annotation.consumer.VariantConsumer;
import org.uichuimi.vcf.variant.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates 3rd normal form tables that can easily being imported into neo4j.
 * <p>
 * <ul>
 * <li><b>samples -> </b>id:ID(sample)</li>
 * <li><b>variant -> </b>:ID(variant), chrom, pos, ref, alt, rs[], sift, polyphen, effect,
 * amino</li>
 * <li><b>frequencies -> </b>:ID(freq), source, population, value:double</li>
 * <li><b>var2freq -> </b>:START_ID(variant), :END_ID(freq)</li>
 * <li><b>var2gene -> </b>:START_ID(variant), :END_ID(gene)</li>
 * <li><b>homozygous -> </b>:START_ID(sample), :END_ID(variant), ad[], dp:int</li>
 * <li><b>heterozygous -> </b>:START_ID(sample), :END_ID(variant), ad[], dp:int</li>
 * <li><b>wildtype -> </b>:START_ID(sample), :END_ID(variant), ad[], dp:int</li>
 * </ul>
 * <i>gene</i> refers to Ensembl gene identifier (ENSG0001234). This class is not responsible of
 * the genes table.
 */
public class Neo4jTablesWriter implements VariantConsumer {

	private final static List<Chromosome> chrs = ChromosomeFactory.getChromosomeList();

	private final static AtomicLong NEXT_ID = new AtomicLong();
	/* name */
	private final TableWriter samples;
	/* sample, variant, type, ref_count, alt_count */
	private final TableWriter genotypes;
	/* variant_id, gene_id */
	private final TableWriter var2gene;
	/* id, chrom, position, ref, alt, rs, change, hgvs, sift, polyphen2, gmaf */
	private final TableWriter variants;
	/* id, population, source, ac, an, af */
	private final TableWriter frequencies;
	/* variant_id, frequency_id */
	private final TableWriter var2freq;
	/* variant_id, effect_id */
	private final TableWriter var2effect;
	/* variant_id, chrom_id */
	private final TableWriter var2chrom;
	/* name:id, index  */
	private final TableWriter chromosomes;

	private VcfHeader header;

	private final AtomicLong frequencyId = new AtomicLong();
	private final List<TableWriter> tables;

	private static final List<String> DATABASES = List.of("gnomAD genomes", "gnomAD exomes");
	private static final List<String> DB_PREFIXES = List.of("GG", "GE");
	private static final List<String> POP_PREFIXES = List.of("afr", "ami", "oth", "sas", "asj", "fin", "nfe", "eas");

//	private static final List<String> KEYS = List.of("KG_AF", "GG_AF", "GE_AF", "EX_AF");
//	private static final List<List<String>> POPULATIONS = List.of(
//			KGenomesAnnotator.POPULATIONS,
//			GnomadGenomeAnnotator.POPULATIONS,
//			GnomadExomeAnnotator.POPULATIONS,
//			ExACAnnotator.POPULATIONS);

	public Neo4jTablesWriter(File path) throws IOException {

		samples = new TableWriter(new File(path, "Persons.tsv.gz"), Collections.singletonList("identifier:ID(sample)"));
		samples.createIndex(0);

		// (:Sample)-[:genotype]->(:Variant)
		final List<String> GT_COLS = List.of(":TYPE", ":START_ID(sample)", ":END_ID(variant)", "REF_COUNT", "ALT_COUNT");
		genotypes = new TableWriter(new File(path, "genotypes.tsv.gz"), GT_COLS);

		// (:Variant)-[:CHROMOSOME]->(:Chromosome)
		var2chrom = new TableWriter(new File(path, "variant2chromosome.tsv.gz"), List.of(":START_ID(variant)", ":END_ID(chromosome)"));

		// (:Chromosome)
		chromosomes = new TableWriter(new File(path, "Chromosomes.tsv.gz"), List.of("name:ID(chromosome)", "index:int"));

		// (:Variant)
		final List<String> cols = new ArrayList<>(List.of(":ID(variant)", "chrom:string",
				"chromIndex:int", "pos:int", "ref:string", "alt:string", "identifier:string",
				"sift:string", "polyphen:string", "amino:string", "hgvsp:string", "gmaf:double"));
		variants = new TableWriter(new File(path, "Variants.tsv.gz"), cols);

		var2effect = new TableWriter(new File(path, "var2effect.tsv.gz"), List.of(":START_ID(variant)", ":END_ID(effect)"));

		// (:Variant)-[:gene]->(:Gene)
		var2gene = new TableWriter(new File(path, "var2gene.tsv.gz"), List.of(":START_ID(variant)", ":END_ID(gene)"));

		// (:Variant)-[:FREQUENCY]->(:Frequency)
		frequencies = new TableWriter(new File(path, "Frequencies.tsv.gz"), List.of(":ID(freq)", "source", "population", "an:int", "ac:int", "af:double"));
		var2freq = new TableWriter(new File(path, "var2freq.tsv.gz"), List.of(":START_ID(variant)", ":END_ID(freq)"));
		tables = List.of(this.samples, var2gene, variants, frequencies, var2freq, var2effect, genotypes);
	}

	@Override
	public void start(VcfHeader header) {
		this.header = header;
		try {
			writeSamples(header);
			writeChromosomes(header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeSamples(VcfHeader header) throws IOException {
		for (String sample : header.getSamples())
			samples.write(sample);
		samples.close();
	}

	private void writeChromosomes(VcfHeader header) throws IOException {
		// Write
		final List<String> chromosomeList = new ArrayList<>();
		for (Chromosome chr : chrs)
			chromosomeList.add(chr.getName());
		header.getComplexLines().get("contig").forEach((key, headerLine) -> {
			// Call Chromosome factory to find the best match
			final String name = headerLine.getValue("ID");
			final Chromosome chromosome = Chromosome.get(name, Chromosome.Namespace.GRCH);
			if (!chromosomeList.contains(chromosome.getName()))
				chromosomeList.add(chromosome.getName());
		});
		for (int i = 0; i < chromosomeList.size(); i++)
			chromosomes.write(chromosomeList.get(i), i);
		chromosomes.close();
	}

	@Override
	public void accept(Variant variant) {
		try {
			for (int r = 0; r < variant.getReferences().size(); r++)
				for (int a = 0; a < variant.getAlternatives().size(); a++)
					addSimplifiedVariant(variant, r, a);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Adds a variant to the variants table. Since third normal form requires only 1 reference
	 * allele and 1 alternative allele, this method should be called <em>r * a</em> times, to export
	 * all combinations in the same variant.
	 *
	 * @param variant the variant
	 * @param r       index of reference allele in variant.references
	 * @param a       index of alternative allele in variant.alternatives
	 * @throws IOException if any writer is closed
	 */
	private void addSimplifiedVariant(Variant variant, int r, int a) throws IOException {
		if (!filter(variant, r, a)) return;
		final int absoluteA = variant.getReferences().size() + a;
		final String ref = variant.getReferences().get(r);
		final String alt = variant.getAlternatives().get(a);
		final String chrom = variant.getCoordinate().getChromosome().getName();
		final long position = variant.getCoordinate().getPosition();

		final String variantId = String.format("%s:%s:%s:%s", chrom, position, ref, alt);

		Double gmaf = null;
		for (int i = 0; i < DATABASES.size(); i++) {
			for (String popPrefix : POP_PREFIXES) {
				final Double f = writeFrequency(variant, variantId, a, DATABASES.get(i), DB_PREFIXES.get(i), popPrefix);
				if (f != null) {
					if (gmaf == null) gmaf = f;
					else gmaf = Double.max(gmaf, f);
				}
			}
		}
		writeVariant(variantId, variant, a, r, gmaf);

		writeConsequence(variant, variantId, a);
		writeGene(variant, variantId, a);
		writeSamples(variant, variantId, r, absoluteA);

		writeChromosome(variant, variantId);
	}

	private Double writeFrequency(Variant variant, String variantId, int a, String db_name, String db_prefix, String pop) throws IOException {
		final String AC = String.format("%s_AC_%s", db_prefix, pop);
		final Integer ac = getIntegerValue(a, variant.getInfo(AC));
		if (ac == null) return null;
		final String AN = String.format("%s_AN_%s", db_prefix, pop);
		final Integer an = getIntegerValue(a, variant.getInfo(AN));
		if (an == null) return null;
		final long id = frequencyId.incrementAndGet();
		var2freq.write(variantId, id);
		final double af = (double) ac / an;
		// ":ID(freq)", "source", "population", "an:int", "ac:int", "af:double"
		frequencies.write(id, db_name, pop, an, ac, af);
		return af;
	}

	@Nullable
	private Integer getIntegerValue(int a, Object ac) {
		if (ac == null) return null;
		final Integer aac;
		if (ac instanceof Integer) aac = (Integer) ac;
		else if (ac instanceof List) {
			final List<Integer> lac = (List<Integer>) ac;
			if (lac.size() < a) return null;
			aac = lac.get(a);
		} else aac = null;
		if (aac == null) return null;
		return aac;
	}

	private boolean filter(Variant variant, int r, int a) {
		final List<String> cons = variant.getInfo("CONS");
		if (cons == null || cons.size() < a || cons.get(a) == null || cons.get(a).equals("intergenic_variant"))
			return false;
		if (variant.getAlternatives().get(a).equals("*")) return false;
		// variant passes if there is at least
		//      one homozygous sample with dp >= 5
		// or   one heterozygous sample with dp >= 10
		final int alleleIndex = 1 + a;
		for (Info info : variant.getSampleInfo()) {
			final String gt = info.get("GT");
			if (gt == null) continue;
			final Genotype genotype = Genotype.create(gt);
			final Integer dp = info.get("DP");
			if (genotype.getA() == (genotype.getB()) && genotype.getA() == alleleIndex) {
				// homozygous only for this allele (1 + a)
				if (dp >= 10) return true;
			} else if (genotype.getA() == alleleIndex || genotype.getB() == alleleIndex) {
				// heterozygous
				if (dp >= 5) return true;
			}
		}
		return false;
	}

	private String writeVariant(String variantId, Variant variant, int a, int r, Double gmaf) throws IOException {
		final String ref = variant.getReferences().get(r);
		final String alt = variant.getAlternatives().get(a);
		final String chrom = variant.getCoordinate().getChromosome().getName();
		final long position = variant.getCoordinate().getPosition();

		final List<String> sift = variant.getInfo(AnnotationConstants.dbNSFP_SIFT);
		final List<String> phen = variant.getInfo(AnnotationConstants.dbNSFP_POLYPHEN);
		final List<String> amino = variant.getInfo(AnnotationConstants.AMINO);
		final List<String> hgvs = variant.getInfo(AnnotationConstants.dbNSFP_HGVSp);
		final String identifier = variant.getIdentifiers().isEmpty()
				? "n" + NEXT_ID.incrementAndGet()
				: variant.getIdentifiers().get(0);
		final Integer chromIndex = chrs.indexOf(variant.getCoordinate().getChromosome());
		// ":ID(variant)", "chrom:string", "chromIndex:int", "pos:int", "ref:string", "alt:string",
		// "identifier:string", "sift:string", "polyphen:string", "amino:string", "hgvsp:string", "gmaf:double"
		variants.write(variantId, chrom, chromIndex, position, ref, alt,
				identifier,
				sift == null ? null : sift.get(a),
				phen == null ? null : phen.get(a),
				amino == null ? null : amino.get(a),
				hgvs == null ? null : hgvs.get(a),
				gmaf
		);
		// Write Canary frequency
		final List<Integer> ac = variant.getInfo("AC");
		if (ac != null) {
			if (ac.size() >= a) {
				final Integer aac = ac.get(a);
				if (aac != null) {
					final long fid = frequencyId.incrementAndGet();
					final Integer an = variant.getInfo("AN");
					var2freq.write(variantId, fid);
					final double af = (double) aac / an;
					// ":ID(freq)", "source", "population", "an:int", "ac:int", "af:double"
					frequencies.write(fid, "VarCan", "can", an, ac, af);
				}
			}
		}
		return variantId;
	}

	private void writeGene(Variant variant, String variantId, int a) throws IOException {
		final List<String> genes = variant.getInfo(AnnotationConstants.ENSG);
		if (genes == null) return;
		final String ensg = genes.get(a);
		if (ensg != null) var2gene.write(variantId, ensg);
	}

	private void writeConsequence(Variant variant, String variantId, int a) throws IOException {
		// CONS is expected to be Number=A or Number=.
		final List<String> cons = variant.getInfo(AnnotationConstants.CONS);
		if (cons == null) return;
		final String effect = cons.get(a);
		if (effect != null) var2effect.write(variantId, effect);
	}

	private void writeSamples(Variant variant, String variantId, int r, int absoluteA) throws IOException {
		for (int i = 0; i < header.getSamples().size(); i++) {
			final String sample = header.getSamples().get(i);
			final Info sampleInfo = variant.getSampleInfo(i);
			final String gt = sampleInfo.get(AnnotationConstants.GT);
			if (gt == null || gt.equals("./.") || gt.equals(".")) continue;

			final Genotype genotype = Genotype.create(gt);

			final String alleleDepth = getAlleleDepth(r, absoluteA, sampleInfo);
			final int readDepth = getReadDepth(sampleInfo);
			genotypes.write(genotype.getType(), sample, variantId, alleleDepth, readDepth);
		}
	}

	private Double writeFrequencies(Variant variant, String variantId, int a, String database, String key, List<String> populations) throws IOException {
		// Frequencies are Number=A, so a must be position in alternatives
		final List<String> freqs = variant.getInfo(key);
		if (freqs == null) return null;
		if (freqs.size() <= a) return null;
		final String fr = freqs.get(a);
		if (fr == null || fr.equals(VcfConstants.EMPTY_VALUE)) return null;
		final String[] values = fr.split(AnnotationConstants.ESCAPED_DELIMITER);
		Double max = null;
		for (int p = 0; p < populations.size(); p++) {
			final String value = values[p];
			if (value.equals(VcfConstants.EMPTY_VALUE)) continue;
			final long id = frequencyId.incrementAndGet();
			var2freq.write(variantId, id);
			frequencies.write(id, database, populations.get(p), value);
			max = Double.max(max == null ? 0 : max, Double.parseDouble(value));
		}
		return max;
	}

	@NotNull
	private Integer getReadDepth(Info sampleInfo) {
		final Integer dp = sampleInfo.get(AnnotationConstants.DP);
		return dp == null ? 0 : dp;
	}

	private String getAlleleDepth(int r, int absoluteA, Info sampleInfo) {
		final int refAd;
		final int altAd;
		final List<Integer> ad = sampleInfo.get(AnnotationConstants.AD);
		if (ad == null) {
			refAd = 0;
			altAd = 0;
		} else {
			refAd = ad.get(r);
			// if the sample has depth count for the reference but not for this allele
			altAd = ad.get(absoluteA) == null ? 0 : ad.get(absoluteA);
		}
		return String.format("%d,%d", refAd, altAd);
	}

	private void writeChromosome(Variant variant, String variantId) throws IOException {
		var2chrom.write(variantId, variant.getCoordinate().getChromosome().getName());
	}

	@Override
	public void close() {
		for (TableWriter table : tables) table.close();
	}
}
