package com.netflix.imflibrary.st2067_2;

import com.netflix.imflibrary.IMFErrorLogger;
import com.netflix.imflibrary.IMFErrorLoggerImpl;
import com.netflix.imflibrary.utils.DOMNodeObjectModel;
import com.netflix.imflibrary.utils.UUIDHelper;
import com.netflix.imflibrary.utils.Utilities;
import org.w3c.dom.Node;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A class that performs 2013 CoreConstraints st2067-2:2013 related checks on the elements of a Composition Playlist such as VirtualTracks, Segments, Sequences and Resources.
 */
final class IMFCoreConstraintsChecker {


    //To prevent instantiation
    private IMFCoreConstraintsChecker(){

    }

    public static List checkVirtualTracks(IMFCompositionPlaylistType compositionPlaylistType,
                                          Map<UUID, ? extends Composition.VirtualTrack> virtualTrackMap,
                                          Map<UUID, DOMNodeObjectModel> essenceDescriptorListMap){

        boolean foundMainImageEssence = false;
        int numberOfMainImageEssences = 0;
        boolean foundMainAudioEssence = false;
        int numberOfMarkerSequences = 0;
        IMFErrorLogger imfErrorLogger =new IMFErrorLoggerImpl();
        Iterator iterator = virtualTrackMap.entrySet().iterator();
        while(iterator.hasNext()) {
            Composition.VirtualTrack virtualTrack = ((Map.Entry<UUID, ? extends Composition.VirtualTrack>) iterator.next()).getValue();

            List<? extends IMFBaseResourceType> virtualTrackResourceList = virtualTrack.getResourceList();
            imfErrorLogger.addAllErrors(checkVirtualTrackResourceList(virtualTrack.getTrackID(), virtualTrackResourceList));

            if (virtualTrack.getSequenceTypeEnum().equals(Composition.SequenceTypeEnum.MainImageSequence)) {
                foundMainImageEssence = true;
                numberOfMainImageEssences++;
                Composition.EditRate compositionEditRate = compositionPlaylistType.getEditRate();
                for (IMFBaseResourceType baseResourceType : virtualTrackResourceList) {
                    Composition.EditRate trackResourceEditRate = baseResourceType.getEditRate();
                    if (trackResourceEditRate != null
                            && !trackResourceEditRate.equals(compositionEditRate)) {
                        imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, String.format("This Composition is invalid since the CompositionEditRate %s is not the same as atleast one of the MainImageSequence's Resource EditRate %s. Please refer to st2067-2:2013 Section 6.4", compositionEditRate.toString(), trackResourceEditRate.toString()));
                    }
                }
            }
            else if(virtualTrack.getSequenceTypeEnum().equals(Composition.SequenceTypeEnum.MainAudioSequence)){
                foundMainAudioEssence = true;
            }

            if((virtualTrack.getSequenceTypeEnum().equals(Composition.SequenceTypeEnum.MainImageSequence)
                    || virtualTrack.getSequenceTypeEnum().equals(Composition.SequenceTypeEnum.MainAudioSequence))
                    && compositionPlaylistType.getEssenceDescriptorList() != null
                    && compositionPlaylistType.getEssenceDescriptorList().size() > 0)
            {
                List<DOMNodeObjectModel> virtualTrackEssenceDescriptors = new ArrayList<>();
                String refSourceEncodingElement = "";
                String essenceDescriptorField = "";
                String otherEssenceDescriptorField = "";
                Composition.EditRate essenceEditRate = null;
                for(IMFBaseResourceType imfBaseResourceType : virtualTrackResourceList){

                    IMFTrackFileResourceType imfTrackFileResourceType = IMFTrackFileResourceType.class.cast(imfBaseResourceType);
                    DOMNodeObjectModel domNodeObjectModel = essenceDescriptorListMap.get(UUIDHelper.fromUUIDAsURNStringToUUID(imfTrackFileResourceType.getSourceEncoding()));
                    if(domNodeObjectModel == null){
                        imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL,
                                String.format("This Composition represented by the ID %s is invalid since the VirtualTrack represented by ID %s has a Resource represented by ID %s that refers to a EssenceDescriptor in the CPL's EssenceDescriptorList that should be represented by the ID %s" +
                                                ", however the EssenceDescriptorList does not contain any Essence Descriptors with this ID"
                                        , compositionPlaylistType.getId().toString(), virtualTrack.getTrackID().toString(), imfBaseResourceType.getId(), imfTrackFileResourceType.getSourceEncoding()));
                    }
                    else {

                        if (!refSourceEncodingElement.equals(imfTrackFileResourceType.getSourceEncoding())) {
                            refSourceEncodingElement = imfTrackFileResourceType.getSourceEncoding();
                            //Section 6.3.1 st2067-2:2016 Edit Rate check
                            if (virtualTrack.getSequenceTypeEnum().equals(Composition.SequenceTypeEnum.MainImageSequence)) {
                                essenceDescriptorField = "SampleRate";
                            } else if (virtualTrack.getSequenceTypeEnum().equals(Composition.SequenceTypeEnum.MainAudioSequence)) {
                                essenceDescriptorField = "SampleRate";
                                otherEssenceDescriptorField = "AudioSampleRate";
                            }
                            Map<DOMNodeObjectModel.DOMNodeElementTuple, Map<String, Integer>> fields = domNodeObjectModel.getFields();
                            Iterator<Map.Entry<DOMNodeObjectModel.DOMNodeElementTuple, Map<String, Integer>>> entryIterator = fields.entrySet().iterator();
                            while (entryIterator.hasNext()) {
                                Map.Entry<DOMNodeObjectModel.DOMNodeElementTuple, Map<String, Integer>> entry = entryIterator.next();
                                if (entry.getKey().getLocalName().equals(essenceDescriptorField)
                                        || entry.getKey().getLocalName().equals(otherEssenceDescriptorField)) {
                                    if (entry.getValue().size() > 1) {
                                        imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL,
                                                String.format("This Composition represented by the ID %s is invalid since the VirtualTrack represented by ID %s has a Resource represented by ID %s that refers to a EssenceDescriptor in the CPL's EssenceDescriptorList represented by the ID %s" +
                                                                " with more than 1 value for the %s field"
                                                        , compositionPlaylistType.getId().toString(), virtualTrack.getTrackID().toString(), imfBaseResourceType.getId(), imfTrackFileResourceType.getSourceEncoding(), essenceDescriptorField));
                                    } else {
                                        String sampleRate = entry.getValue().keySet().iterator().next();
                                        Long numerator = 0L;
                                        Long denominator = 0L;
                                        String[] sampleRateElements = (sampleRate.contains(" ")) ? sampleRate.split(" ") : sampleRate.contains("/") ? sampleRate.split("/") : new String[2];
                                        if (sampleRateElements.length == 2) {
                                            numerator = Long.valueOf(sampleRateElements[0]);
                                            denominator = Long.valueOf(sampleRateElements[1]);
                                        } else if (sampleRateElements.length == 1) {
                                            numerator = Long.valueOf(sampleRateElements[0]);
                                            denominator = 1L;
                                        }
                                        List<Long> editRate = new ArrayList<>();
                                        editRate.add(numerator);
                                        editRate.add(denominator);
                                        essenceEditRate = new Composition.EditRate(editRate);
                                    }
                                }
                            }
                        }
                        if (essenceEditRate == null) {
                            imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL,
                                    String.format("This Composition represented by the ID %s is invalid since the VirtualTrack represented by ID %s has a Resource represented by ID %s that seems to refer to a EssenceDescriptor in the CPL's EssenceDescriptorList represented by the ID %s " +
                                                    "which does not have a value set for the field %s, however the Resource Edit Rate is %s"
                                            , compositionPlaylistType.getId().toString(), virtualTrack.getTrackID().toString(), imfBaseResourceType.getId(), imfTrackFileResourceType.getSourceEncoding(), essenceDescriptorField, imfBaseResourceType.getEditRate().toString()));
                        } else if (!essenceEditRate.equals(imfBaseResourceType.getEditRate())) {
                            imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL,
                                    String.format("This Composition represented by the ID %s is invalid since the VirtualTrack represented by ID %s has a Resource represented by ID %s that refers to a EssenceDescriptor in the CPL's EssenceDescriptorList represented by the ID %s " +
                                                    "whose indicated %s value is %s, however the Resource Edit Rate is %s"
                                            , compositionPlaylistType.getId().toString(), virtualTrack.getTrackID().toString(), imfBaseResourceType.getId(), imfTrackFileResourceType.getSourceEncoding(), essenceDescriptorField, essenceEditRate.toString(), imfBaseResourceType.getEditRate().toString()));
                        }
                        virtualTrackEssenceDescriptors.add(domNodeObjectModel);
                    }
                }

                if(!(virtualTrackEssenceDescriptors.size() > 0)){
                    imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL,
                            String.format("This Composition represented by the ID %s is invalid since the resources comprising the VirtualTrack represented by ID %s seem to refer to EssenceDescriptor/s in the CPL's EssenceDescriptorList that are absent", compositionPlaylistType.getId().toString(), virtualTrack.getTrackID().toString()));
                }
                else {
                    Set<String> ignoreSet = new HashSet<>();
                    ignoreSet.add("InstanceUID");
                    ignoreSet.add("EssenceLength");
                    boolean isVirtualTrackHomogeneous = true;
                    DOMNodeObjectModel refDOMNodeObjectModel = virtualTrackEssenceDescriptors.get(0).createDOMNodeObjectModelIgnoreSet(virtualTrackEssenceDescriptors.get(0), ignoreSet);
                    for (int i = 1; i < virtualTrackEssenceDescriptors.size(); i++) {
                        isVirtualTrackHomogeneous &= refDOMNodeObjectModel.equals(virtualTrackEssenceDescriptors.get(i).createDOMNodeObjectModelIgnoreSet(virtualTrackEssenceDescriptors.get(i), ignoreSet));
                    }
                    if (!isVirtualTrackHomogeneous) {
                        imfErrorLogger.addAllErrors(refDOMNodeObjectModel.getErrors());
                        imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL,
                                String.format("This Composition represented by the ID %s is invalid since the VirtualTrack represented by ID %s is not homogeneous based on a comparison of the EssenceDescriptors referenced by its resources in the Essence Descriptor List", compositionPlaylistType.getId().toString(), virtualTrack.getTrackID().toString()));
                    }
                }
            }
        }

        //TODO : Add a check to ensure that all the VirtualTracks have the same duration.
        //Section 6.3.1 st2067-2:2016 and Section 6.9.3 st2067-3:2016
        if(!foundMainImageEssence){
            imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, String.format("The Composition represented by Id %s does not contain a single image essence in its first segment, exactly one is required", compositionPlaylistType.getId().toString()));
        }
        else{
            if(numberOfMainImageEssences > 1){
                imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, String.format("The Composition represented by Id %s seems to contain %d image essences in its first segment, exactly one is required", compositionPlaylistType.getId().toString(), numberOfMainImageEssences));
            }
        }

        //Section 6.3.2 st2067-2:2016 and Section 6.9.3 st2067-3:2016
        if(!foundMainAudioEssence){
            imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, String.format("The Composition represented by Id %s does not contain a single audio essence in its first segment, one or more is required", compositionPlaylistType.getId().toString()));
        }

        return imfErrorLogger.getErrors();
    }

    public static void checkSegments(IMFCompositionPlaylistType compositionPlaylistType, Map<UUID, Composition.VirtualTrack> virtualTrackMap, @Nullable IMFErrorLogger imfErrorLogger)
    {
        for (IMFSegmentType segment : compositionPlaylistType.getSegmentList())
        {
            Set<UUID> trackIDs = new HashSet<>();

            /* TODO: Add check for Marker sequence */
            Set<Long> sequencesDurationSet = new HashSet<>();
            double compositionEditRate = (double)compositionPlaylistType.getEditRate().getNumerator()/compositionPlaylistType.getEditRate().getDenominator();
            for (IMFSequenceType sequence : segment.getSequenceList())
            {
                UUID uuid = UUIDHelper.fromUUIDAsURNStringToUUID(sequence.getTrackId());
                trackIDs.add(uuid);
                if (virtualTrackMap.get(uuid) == null)
                {
                    //Section 6.9.3 st2067-3:2016
                    String message = String.format(
                            "Segment %s in Composition XML file contains virtual track UUID %s, which does not appear in all the segments of the Composition, this is invalid",
                            segment.getId(), uuid);
                    imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CORE_CONSTRAINTS_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, message);
                }
                List<? extends IMFBaseResourceType> resources = sequence.getResourceList();
                Long sequenceDurationInCompositionEditUnits = 0L;
                Double sequenceDuration = 0.0;
                for(IMFBaseResourceType imfBaseResourceType : resources){
                    double resourceEditRate = (double)imfBaseResourceType.getEditRate().getNumerator()/imfBaseResourceType.getEditRate().getDenominator();
                    sequenceDuration += (double)(imfBaseResourceType.getDuration() * compositionEditRate)/resourceEditRate;
                }
                sequenceDurationInCompositionEditUnits = Math.round(sequenceDuration);
                sequencesDurationSet.add(sequenceDurationInCompositionEditUnits);

            }
            //Section 7.3 st2067-3:2016
            if(sequencesDurationSet.size() > 1){
                imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CORE_CONSTRAINTS_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL,
                        String.format("Segment represented by the Id %s seems to have sequences that are not of the same duration, following sequence durations were computed based on the information in the Sequence List for this Segment %s represented in Composition Edit Units", segment.getId(), Utilities.serializeObjectCollectionToString(sequencesDurationSet)));
            }

            if (trackIDs.size() != virtualTrackMap.size())
            {
                String message = String.format(
                        "Number of distinct virtual trackIDs in a segment = %s, different from first segment %d", trackIDs.size(), virtualTrackMap.size());
                imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CORE_CONSTRAINTS_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.NON_FATAL, message);
            }

        }
    }

    public static List checkVirtualTrackResourceList(UUID trackID, List<? extends IMFBaseResourceType>
            virtualBaseResourceList){
        IMFErrorLogger imfErrorLogger = new IMFErrorLoggerImpl();
        if(virtualBaseResourceList == null
                || virtualBaseResourceList.size() == 0){
            imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, String.format("VirtualTrack with ID %s does not have any associated resources this is invalid", trackID.toString()));
            return imfErrorLogger.getErrors();
        }
        Set<Composition.EditRate> editRates = new HashSet<>();
        Composition.EditRate baseResourceEditRate = null;
        for(IMFBaseResourceType baseResource : virtualBaseResourceList){
            long compositionPlaylistResourceIntrinsicDuration = baseResource.getIntrinsicDuration().longValue();
            long compositionPlaylistResourceEntryPoint = (baseResource.getEntryPoint() == null) ? 0L : baseResource.getEntryPoint().longValue();
            //Check to see if the Resource's source duration value is in the valid range as specified in st2067-3:2013 section 6.11.6
            if(baseResource.getSourceDuration() != null){
                if(baseResource.getSourceDuration().longValue() < 0
                        || baseResource.getSourceDuration().longValue() > (compositionPlaylistResourceIntrinsicDuration - compositionPlaylistResourceEntryPoint)){
                    imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR,
                            IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, String.format("VirtualTrack with ID %s has a resource with ID %s, that has an invalid source duration value %d, should be in the range [0,%d]",
                                    trackID.toString(),
                                    baseResource.getId(),
                                    baseResource.getSourceDuration().longValue(),
                                    (compositionPlaylistResourceIntrinsicDuration - compositionPlaylistResourceEntryPoint)));
                }
            }

            //Check to see if the Marker Resource's intrinsic duration value is in the valid range as specified in st2067-3:2013 section 6.13
            if (baseResource instanceof IMFMarkerResourceType) {
                IMFMarkerResourceType markerResource = IMFMarkerResourceType.class.cast(baseResource);
                List<IMFMarkerType> markerList = markerResource.getMarkerList();
                for (IMFMarkerType marker : markerList) {
                    if (marker.getOffset().longValue() >= markerResource.getIntrinsicDuration().longValue()) {
                        imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger
                                .IMFErrors.ErrorLevels.FATAL, String.format("VirtualTrack with ID %s  has a  " +
                                        "resource with ID %s, that has a marker %s, that has an invalid offset " +
                                        "value %d, should be in the range [0,%d] ",
                                trackID.toString(),
                                markerResource.getId(), marker.getLabel().getValue(), marker
                                        .getOffset().longValue(), markerResource.getIntrinsicDuration().longValue()-1));
                    }
                }
            }

            baseResourceEditRate = baseResource.getEditRate();
            if(baseResourceEditRate != null){
                editRates.add(baseResourceEditRate);
            }
        }

        if(editRates.size() > 1){
            StringBuilder editRatesString = new StringBuilder();
            Iterator iterator = editRates.iterator();
            while(iterator.hasNext()){
                editRatesString.append(iterator.next().toString());
                editRatesString.append(String.format("%n"));
            }
            imfErrorLogger.addError(IMFErrorLogger.IMFErrors.ErrorCodes.IMF_CPL_ERROR, IMFErrorLogger.IMFErrors.ErrorLevels.FATAL, String.format("VirtualTrack with ID %s has resources with inconsistent editRates %s", trackID.toString(), editRatesString.toString()));
        }
        return imfErrorLogger.getErrors();
    }
}
