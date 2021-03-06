package org.uichuimi.vcf.utils.annotation.consumer;

import org.uichuimi.vcf.header.VcfHeader;
import org.uichuimi.vcf.utils.annotation.AnnotationConstants;
import org.uichuimi.vcf.utils.annotation.Genotype;
import org.uichuimi.vcf.variant.Variant;

import java.util.Arrays;

import static org.uichuimi.vcf.utils.annotation.AnnotationConstants.DP;

public class StatsCalculator implements VariantConsumer {

	private VcfHeader header;

	@Override
	public void start(VcfHeader header) {
		this.header = header;
	}

	@Override
	public void accept(Variant variant) {
		dp(variant);
		ac(variant);
//		qd(variant);
	}

	private void dp(Variant variant) {
		int dp = 0;
		for (int i = 0; i < header.getSamples().size(); i++) {
			if (variant.getSampleInfo(i).contains(DP))
				dp += variant.getSampleInfo(i).<Integer>get(DP);
		}
		variant.setInfo(DP, dp);
	}

	private void ac(Variant variant) {
		// Alternative allele count
		// AC, number of times an allele has been observed
		// AN, number of alleles observed, including reference
		int[] ac = new int[variant.getAlleles().size()];
		for (int s = 0; s < header.getSamples().size(); s++) {
			final String gt = variant.getSampleInfo(s).get(AnnotationConstants.GT);
			if (gt == null) continue;
			final Genotype genotype = Genotype.create(gt);
			ac[genotype.getA()]++;
			ac[genotype.getB()]++;
		}
		// This do take into account all alleles, including reference
		final int an = Arrays.stream(ac).sum();
		variant.setInfo(AnnotationConstants.AN, an);
		// Skip the reference allele, since AC has Number=A
		final Integer[] acArray = new Integer[variant.getAlternatives().size()];
		final Float[] afArray = new Float[variant.getAlternatives().size()];
		for (int a = 1; a < ac.length; a++) {
			acArray[a-1] = ac[a];
			afArray[a-1] = (float) ac[a] / an;
		}
		variant.setInfo(AnnotationConstants.AC, Arrays.asList(acArray));
		variant.setInfo(AnnotationConstants.AF, Arrays.asList(afArray));
	}

	@Override
	public void close() {

	}
}
