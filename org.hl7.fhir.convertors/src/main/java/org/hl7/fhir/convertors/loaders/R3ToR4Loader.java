package org.hl7.fhir.convertors.loaders;

/*
  Copyright (c) 2011+, HL7, Inc.
  All rights reserved.
  
  Redistribution and use in source and binary forms, with or without modification, 
  are permitted provided that the following conditions are met:
    
   * Redistributions of source code must retain the above copyright notice, this 
     list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above copyright notice, 
     this list of conditions and the following disclaimer in the documentation 
     and/or other materials provided with the distribution.
   * Neither the name of HL7 nor the names of its contributors may be used to 
     endorse or promote products derived from this software without specific 
     prior written permission.
  
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
  POSSIBILITY OF SUCH DAMAGE.
  
 */



import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.convertors.VersionConvertorAdvisor40;
import org.hl7.fhir.convertors.VersionConvertor_30_40;
import org.hl7.fhir.dstu3.formats.JsonParser;
import org.hl7.fhir.dstu3.formats.XmlParser;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.context.SimpleWorkerContext.IContextResourceLoader;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;

public class R3ToR4Loader implements IContextResourceLoader, VersionConvertorAdvisor40 {

  private List<CodeSystem> cslist = new ArrayList<>();
  private boolean patchUrls;
  private boolean killPrimitives;;
  
  @Override
  public Bundle loadBundle(InputStream stream, boolean isJson) throws FHIRException, IOException {
    Resource r3 = null;
    if (isJson)
      r3 = new JsonParser().parse(stream);
    else
      r3 = new XmlParser().parse(stream);
    org.hl7.fhir.r4.model.Resource r4 = VersionConvertor_30_40.convertResource(r3, false);
    
    Bundle b;
    if (r4 instanceof Bundle)
      b = (Bundle) r4;
    else {
      b = new Bundle();
      b.setId(UUID.randomUUID().toString().toLowerCase());
      b.setType(BundleType.COLLECTION);
      b.addEntry().setResource(r4).setFullUrl(r4 instanceof MetadataResource ? ((MetadataResource) r4).getUrl() : null);
    }
    for (CodeSystem cs : cslist) {
      BundleEntryComponent be = b.addEntry();
      be.setFullUrl(cs.getUrl());
      be.setResource(cs);
    }
    if (killPrimitives) {
      List<BundleEntryComponent> remove = new ArrayList<BundleEntryComponent>();
      for (BundleEntryComponent be : b.getEntry()) {
        if (be.hasResource() && be.getResource() instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) be.getResource();
          if (sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE)
            remove.add(be);
        }
      }
      b.getEntry().removeAll(remove);
    }
    if (patchUrls) {
      for (BundleEntryComponent be : b.getEntry()) {
        if (be.hasResource() && be.getResource() instanceof StructureDefinition) {
          StructureDefinition sd = (StructureDefinition) be.getResource();
          sd.setUrl(sd.getUrl().replace("http://hl7.org/fhir/", "http://hl7.org/fhir/3.0/"));
          sd.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/elementdefinition-namespace").setValue(new UriType("http://hl7.org/fhir"));
          for (ElementDefinition ed : sd.getSnapshot().getElement()) 
            patchUrl(ed);
          for (ElementDefinition ed : sd.getDifferential().getElement()) 
            patchUrl(ed);
        }
      }
    }
    return b;
  }

  private void patchUrl(ElementDefinition ed) {
    for (TypeRefComponent tr : ed.getType()) {
      for (CanonicalType s : tr.getTargetProfile()) {
        s.setValue(s.getValue().replace("http://hl7.org/fhir/", "http://hl7.org/fhir/3.0/"));
      }
      for (CanonicalType s : tr.getProfile()) {
        s.setValue(s.getValue().replace("http://hl7.org/fhir/", "http://hl7.org/fhir/3.0/"));
      }
    }    
  }

  @Override
  public boolean ignoreEntry(BundleEntryComponent src) {
    return false;
  }

  @Override
  public org.hl7.fhir.dstu2.model.Resource convertR2(org.hl7.fhir.r4.model.Resource resource) throws FHIRException {
    return null;
  }

  @Override
  public org.hl7.fhir.dstu2016may.model.Resource convertR2016May(org.hl7.fhir.r4.model.Resource resource) throws FHIRException {
    return null;
  }

  @Override
  public Resource convertR3(org.hl7.fhir.r4.model.Resource resource) throws FHIRException {
    return null;
  }

  @Override
  public void handleCodeSystem(CodeSystem cs, ValueSet vs) {
    cs.setId(vs.getId());
    cs.setValueSet(vs.getUrl());
    cslist.add(cs);
    
  }

  @Override
  public CodeSystem getCodeSystem(ValueSet src) {
    return null;
  }

  public boolean isPatchUrls() {
    return patchUrls;
  }

  public R3ToR4Loader setPatchUrls(boolean patchUrls) {
    this.patchUrls = patchUrls;
    return this;
  }

  public boolean isKillPrimitives() {
    return killPrimitives;
  }

  public R3ToR4Loader setKillPrimitives(boolean killPrimitives) {
    this.killPrimitives = killPrimitives;
    return this;
  }

}