#!/bin/bash

# NOTE: ANBG not only "cleaned" the data, they in fact stripped the enclosing double-quotes from:
#       - column names
#       - all values of type integer
#       - all values of type boolean
#       - even all string/text values EXCEPT those that contain comma-s
#

# TODO: - add proper error handling
#       - signal handler
#
function cleanup_tbl_biota {

    local file_input_csv="${1}"
    #echo "file_input_csv: ${file_input_csv}"
    
    local work_dir=`mktemp -d`
    #echo "work_dir: ${work_dir}"

    # NOTE: clean-up/prepare the input CSV:
    #       - 'tail -n +2' to skip over the first line (the CSV header, the names of the columns)
    #       - replace all \ with \\
    #       - replace "True", "False" with "TRUE", "FALSE"      
    #       - trim any leading and trailing whitespaces from each line 
    #       - finally delete the first and the last " from each line
    #
    tail -n +2 "${file_input_csv}" \
        | sed -e 's/\\/\\\\/g' \
        | sed -e 's/"True"/"TRUE"/g' -e 's/"False"/"FALSE"/g' \
        | sed -e 's/^[[:space:]]*//g' -e 's/[[:space:]]*$//g' \
        | sed -e 's/^"//g' -e 's/"$//g' \
              > ${work_dir}/input.csv
    
    local file_output_csv="${work_dir}/output.csv"
    #echo "file_output_csv: ${file_output_csv}"
    
    # NOTE: write the CSV header (creates the tmp output file)
    echo '"intBiotaID","intParentID","vchrEpithet","vchrFullName","vchrYearOfPub","vchrAuthor","vchrNameQualifier","chrElemType","vchrRank","chrKingdomCode","intOrder","vchrParentage","bitChangedComb","bitShadowed","bitUnplaced","bitUnverified","bitAvailableName","bitLiteratureName","dtDateCreated","vchrWhoCreated","dtDateLastUpdated","vchrWhoLastUpdated","txtDistQual","GUID"' \
         > ${file_output_csv}

    # NOTE: process the (cleaned-up) input CSV line-by-line
    cat ${work_dir}/input.csv | while read line
    do

        local field_per_line=`echo "${line}" | sed -e 's/","/\n/g'`
        local fields=`echo "${field_per_line}" | sed -e 's/^$/EEMMPPTTYY/g'` # BUG: | sed -e 's/^[[:space:]]*//g' -e 's/[[:space:]]*$//g'`
        #local fields=`echo "${fix_empty}" | sed -e 's/^[[:space:]]*//g' -e 's/[[:space:]]*$//g'`

        # NOTE: there was a problem with empty lines, when converted into the array bellow the empty lines were simply thrown away
        #local fields_empty=`echo "${fields}" | sed -e 's/^$/EEMMPPTTYY/g'`
        #echo "FIELDS_START: (`echo "${fields}" | wc -l`)"
        #echo "${fields}"
        #echo "FIELDS_END"

        OFS="$IFS"
        IFS=$'\n'
        local record=($fields)
        IFS="$OFS"
        
        # TODO: storing the values in vars should be possible with a simple one-line read?
        local intBiotaID="${record[0]}"
        local intParentID="${record[1]}"
        local vchrEpithet="`echo "${record[2]}" | sed -e 's/^[[:space:]]*//g' -e 's/[[:space:]]*$//g'`" # trim
        local vchrFullName="${record[3]}"
        
        local vchrYearOfPub="${record[4]}"
        if [[ "$vchrYearOfPub" != "EEMMPPTTYY" ]]; then
            local year_of_pub="`echo "$vchrYearOfPub" | sed -e 's/(//g' -e 's/)//g'`"
            vchrYearOfPub="${year_of_pub:0:4}"        
        fi

        local vchrAuthor="${record[5]}"
        local vchrNameQualifier="${record[6]}"
        local chrElemType="`echo "${record[7]}" | sed -e 's/^[[:space:]]*//g' -e 's/[[:space:]]*$//g'`" # trim
        local vchrRank="${record[8]}"
        local chrKingdomCode="`echo "${record[9]}" | sed -e 's/^[[:space:]]*//g' -e 's/[[:space:]]*$//g'`" # trim
        local intOrder="${record[10]}"
        local vchrParentage="${record[11]}"
        local bitChangedComb="${record[12]}"
        local bitShadowed="${record[13]}"
        local bitUnplaced="${record[14]}"
        local bitUnverified="${record[15]}"
        local bitAvailableName="${record[16]}"
        local bitLiteratureName="${record[17]}"

        local date_created="${record[18]}"
        #echo "TEST: date_created: ${date_created}"

        # alternative approach is to simply truncate the existing date string
        local dtDateCreated="${date_created:0:16}"
        # dtDateCreated=`date +"%Y-%m-%d %H:%M" --date="${date_created}"`
        #echo "TEST: dtDateCreated: ${dtDateCreated}"
        
        local vchrWhoCreated="${record[19]}"

        local date_last_updated="${record[20]}"
        #echo "TEST: date_last_updated: ${date_last_updated}"
        local dtDateLastUpdated="${date_last_updated:0:16}"
        # dtDateLastUpdated=`date +"%Y-%m-%d %H:%M" --date="${date_last_updated}"`
        #echo "TEST: dtDateLastUpdated: ${dtDateLastUpdated}"
        
        local vchrWhoLastUpdated="`echo "${record[21]}" | sed -e 's/^[[:space:]]*//g' -e 's/[[:space:]]*$//g'`" # trim
        local txtDistQual="${record[22]}"
        local GUID="${record[23]}"

        # NOTE: ANBG not only "cleaned" the data, they in fact stripped the enclosing double-quotes from:
        #       - column names
        #       - all values of type integer
        #       - all values of type boolean
        #       - even all string/text values EXCEPT those that contain comma-s
        #
        # intbiotaid         integer,
        # intparentid        integer,
        # vchrepithet        text,
        # vchrfullname       text,
        # vchryearofpub      integer,
        # vchrauthor         text,
        # vchrnamequalifier  text,
        # chrelemtype        text,
        # vchrrank           text,
        # chrkingdomcode     text,
        # intorder           integer,
        # vchrparentage      text,
        # bitchangedcomb     boolean,
        # bitshadowed        boolean,
        # bitunplaced        boolean,
        # bitunverified      boolean,
        # bitavailablename   boolean,
        # bitliteraturename  boolean,
        # dtdatecreated      text,
        # vchrwhocreated     text,
        # dtdatelastupdated  text,
        # vchrwholastupdated text,
        # txtdistqual        text,
        # guid               text
        
        # NOTE: write the cleaned/sanitized line to the output file
        echo "${intBiotaID},${intParentID},\"${vchrEpithet}\",\"${vchrFullName}\",${vchrYearOfPub},\"${vchrAuthor}\",\"${vchrNameQualifier}\",\"${chrElemType}\",\"${vchrRank}\",\"${chrKingdomCode}\",${intOrder},\"${vchrParentage}\",${bitChangedComb},${bitShadowed},${bitUnplaced},${bitUnverified},${bitAvailableName},${bitLiteratureName},\"${dtDateCreated}\",\"${vchrWhoCreated}\",\"${dtDateLastUpdated}\",\"${vchrWhoLastUpdated}\",\"${txtDistQual}\",\"${GUID}\"" \
             >> ${file_output_csv}
    done

    # NOTE: ...and finally:
    #       - restore the empty fields (replace EEMMPPTTYY placeholder with nothing)
    #       - dump the final result to stdout
    #
    cat ${file_output_csv} | sed -e 's/EEMMPPTTYY//g' > ${work_dir}/final.csv
    cat ${work_dir}/final.csv
    
    # NOTE: uncomment if you wish to delete the /tmp work_dir  
    # rm -rf ${work_dir}
}

if [ ${#@} -lt 1 ]; then
    echo "USAGE: $0 [tblBiota.csv]"
    echo "       EXAMPLE: $0 tblBiota_20180810.csv"
    echo
    exit 1;
fi

cleanup_tbl_biota "${1}"
