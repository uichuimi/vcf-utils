package org.uichuimi.vcf.utils.consumer;

import org.uichuimi.vcf.header.VcfHeader;
import org.uichuimi.vcf.variant.Coordinate;
import org.uichuimi.vcf.variant.VariantContext;

public interface VariantConsumer {

	void start(VcfHeader header);

	void accept(VariantContext variant, Coordinate grch38);

	void close();
}