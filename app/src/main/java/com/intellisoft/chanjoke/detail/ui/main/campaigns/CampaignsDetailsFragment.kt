package com.intellisoft.chanjoke.detail.ui.main.campaigns

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.intellisoft.chanjoke.MainActivity
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.databinding.FragmentCampaignsDetailsBinding
import com.intellisoft.chanjoke.detail.ui.main.VaccineDetailsAdapter
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.DbRecycler
import com.intellisoft.chanjoke.fhir.data.DbVaccineScheduleChild
import com.intellisoft.chanjoke.fhir.data.DbVaccineScheduleGroup
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.fhir.data.StatusColors
import com.intellisoft.chanjoke.vaccine.BottomSheetDialog
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [CampaignsDetailsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CampaignsDetailsFragment : Fragment(), VaccineDetailsAdapter.OnCheckBoxSelectedListener {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine
    private val formatterClass = FormatterClass()
    private var patientYears:String? = null
    private var patientDob:String? = null
    private var campaignName:String? = null
    private var patientGender:String? = null
    private var selectedVaccineList = ArrayList<String>()
    private var dbRecyclerList = HashSet<DbRecycler>()
    private lateinit var binding: FragmentCampaignsDetailsBinding
    private lateinit var sharedPreferences1: SharedPreferences
    private var immunizationHandler = ImmunizationHandler()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = FragmentCampaignsDetailsBinding.inflate(inflater, container, false)
        fhirEngine = FhirApplication.fhirEngine(requireContext())

        sharedPreferences1 = requireContext()
            .getSharedPreferences(getString(R.string.campaigns),
                Context.MODE_PRIVATE
            )

        patientId = formatterClass.getSharedPref("patientId", requireContext()).toString()

        patientDetailsViewModel = ViewModelProvider(this,
            PatientDetailsViewModelFactory(requireContext().applicationContext as Application,fhirEngine, patientId)
        )[PatientDetailsViewModel::class.java]

        patientGender = formatterClass.getSharedPref("patientGender", requireContext())

        patientYears = formatterClass.getSharedPref("patientYears", requireContext())
        patientDob = formatterClass.getSharedPref("patientDob", requireContext())
        patientDob = formatterClass.getSharedPref("patientDob", requireContext())
        campaignName = sharedPreferences1.getString("campaignName", null)


        binding.tvAdministerVaccine.setOnClickListener {

            if (selectedVaccineList.isNotEmpty()){
                formatterClass.saveSharedPref(
                    "selectedVaccineName",
                    selectedVaccineList.joinToString(","),
                    requireContext())
                formatterClass.saveSharedPref(
                    "selectedUnContraindicatedVaccine",
                    selectedVaccineList.joinToString(","),
                    requireContext())
                formatterClass.saveSharedPref(
                    "workflowVaccinationType",
                    "ROUTINE", requireContext()
                )

                val bottomSheet = BottomSheetDialog()
                fragmentManager?.let { it1 ->
                    bottomSheet.show(it1,
                        "ModalBottomSheet") }
            }else
                Toast.makeText(requireContext(), "Select at least one vaccine", Toast.LENGTH_SHORT).show()

        }

        getCampaign()

        return binding.root
    }

    private fun getCampaign() {

        CoroutineScope(Dispatchers.IO).launch {

            val administeredList = patientDetailsViewModel.getVaccineList()
            val dbVaccineScheduleGroupList = formatterClass.getCampaignList(campaignName, administeredList)

            val dbVaccineScheduleChildList = ArrayList<DbVaccineScheduleChild>()

            val newExpandableListDetail = HashMap<DbVaccineScheduleGroup, List<DbVaccineScheduleChild>>()

            for (group in dbVaccineScheduleGroupList) {
                newExpandableListDetail[group] = group.dbVaccineScheduleChildList
                group.dbVaccineScheduleChildList.forEach {
                    dbVaccineScheduleChildList.add(it)
                }

            }

            val newExpandableListTitle = ArrayList(newExpandableListDetail.keys)

            CoroutineScope(Dispatchers.Main).launch {

                val inflater = LayoutInflater.from(requireContext())


                for (group in newExpandableListTitle){

                    val groupLayout = inflater.inflate(R.layout.vaccination_schedule, null) as RelativeLayout
                    val tvScheduleTime = groupLayout.findViewById<TextView>(R.id.tvScheduleTime)
                    val tvAefi = groupLayout.findViewById<TextView>(R.id.tvAefi)
                    val imageViewSchedule = groupLayout.findViewById<ImageView>(R.id.imageViewSchedule)
                    val recyclerView = groupLayout.findViewById<RecyclerView>(R.id.recyclerView)
                    recyclerView.setHasFixedSize(true)

                    val vaccineSchedule = group.vaccineSchedule
                    val colorCode = group.colorCode

                    val dbRecycler = DbRecycler(recyclerView, vaccineSchedule)
                    dbRecyclerList.add(dbRecycler)

                    // Populate views with data from DbVaccineScheduleGroup
                    tvScheduleTime.text = vaccineSchedule
                    when (colorCode) {
                        StatusColors.GREEN.name -> {
                            imageViewSchedule.setImageResource(R.drawable.ic_action_schedule_green)
                        }
                        StatusColors.AMBER.name -> {
                            imageViewSchedule.setImageResource(R.drawable.ic_action_schedule_amber)
                        }
                        StatusColors.RED.name -> {
                            imageViewSchedule.setImageResource(R.drawable.ic_action_schedule_red)
                        }
                        else -> {
                            imageViewSchedule.setImageResource(R.drawable.ic_action_schedule_normal_dark)
                        }
                    }

                    tvAefi.text = "Aefi(0)"
                    val counter = patientDetailsViewModel.generateCurrentCount(
                        vaccineSchedule,
                        patientId
                    )
                    tvAefi.text = "AEFIs ($counter)"

                    tvAefi.setOnClickListener {

                        val counterInt = counter.toIntOrNull()
//                        if (counterInt != null && counterInt > 0){
                        FormatterClass().saveSharedPref("current_age", vaccineSchedule, requireContext())
                        val intent = Intent(context, MainActivity::class.java)
                        intent.putExtra("functionToCall", NavigationDetails.LIST_AEFI.name)
                        intent.putExtra("patientId", patientId)
                        startActivity(intent)
//                        }else{
//                            Toast.makeText(requireContext(), "The schedule does not have any AEFI(s)", Toast.LENGTH_SHORT).show()
//                        }

                    }

                    groupLayout.setOnClickListener {

                        performVisibility(vaccineSchedule)
                        generateVaccineList(vaccineSchedule, recyclerView, dbVaccineScheduleChildList)

                    }

                    // Add the cardview_item to linearLayoutId2
                    binding.groupLayout.addView(groupLayout)

                    if (patientDob != null){
                        try{
                            val numberOfBirthWeek = formatterClass.calculateWeeksFromDate(patientDob!!)

                            if (numberOfBirthWeek != null){
                                val numberOfWeek = formatterClass.convertVaccineScheduleToWeeks(vaccineSchedule)

                                if (formatterClass.isWithinPlusOrMinus14(numberOfBirthWeek, numberOfWeek)){
                                    generateVaccineList(vaccineSchedule, recyclerView, dbVaccineScheduleChildList)
                                }
                            }
                        }catch (e:Exception){
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }
    private fun generateVaccineList(
        vaccineSchedule: String,
        recyclerView: RecyclerView,
        dbVaccineScheduleChildList: ArrayList<DbVaccineScheduleChild>
    ) {
        val patientGender = formatterClass.getSharedPref("patientGender", requireContext())

        //Remove HPV if it's a male
        if (patientGender != null && patientGender == "male"){
            dbVaccineScheduleChildList.removeIf { it.vaccineName.contains("HPV") }
        }


        val adapter = VaccineDetailsAdapter(
            patientDetailsViewModel,
            dbVaccineScheduleChildList,
            this@CampaignsDetailsFragment,
            requireContext())

        recyclerView.adapter = adapter

    }
    private fun performVisibility(vaccineSchedule:String) {

        dbRecyclerList.forEach {

            val recyclerView = it.recyclerView
            val dbVaccineSchedule = it.vaccineSchedule

            if (vaccineSchedule == dbVaccineSchedule){
                if (recyclerView.visibility == View.VISIBLE){
                    recyclerView.visibility = View.GONE
                }else{
                    recyclerView.visibility = View.VISIBLE
                }
            }else{

                recyclerView.visibility = View.GONE
            }
        }

        selectedVaccineList.clear()
        updateList()


    }
    private fun updateList(){
        val administerText = "Administer (${selectedVaccineList.size})"
        binding.tvAdministerVaccine.text = administerText
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CampaignsDetailsFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            CampaignsDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onCheckBoxSelected(position: Int, isChecked: Boolean, vaccineName: String) {

        if (isChecked){
            selectedVaccineList.add(vaccineName)
        }else{
            selectedVaccineList.remove(vaccineName)
        }

//        updateList()

    }
}