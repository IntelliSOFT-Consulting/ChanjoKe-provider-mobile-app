package com.intellisoft.chanjoke.detail.ui.main.referrals

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.google.gson.Gson
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.databinding.FragmentReferralsBinding
import com.intellisoft.chanjoke.detail.PatientDetailActivity
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.DbTempData
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import com.intellisoft.chanjoke.viewmodel.ScreenerViewModel

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ReferralsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ReferralsFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    private val viewModel: ScreenerViewModel by viewModels()
    private lateinit var binding: FragmentReferralsBinding
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine
    private val formatterClass = FormatterClass()
    private lateinit var layoutManager: RecyclerView.LayoutManager
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentReferralsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)

        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = "Referrals"
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        setHasOptionsMenu(true)
        onBackPressed()

        fhirEngine = FhirApplication.fhirEngine(requireContext())
        patientId = formatterClass.getSharedPref("patientId", requireContext()).toString()
        patientDetailsViewModel = ViewModelProvider(
            this,
            PatientDetailsViewModelFactory(
                requireContext().applicationContext as Application,
                fhirEngine,
                patientId
            )
        )[PatientDetailsViewModel::class.java]
        layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.aefiParentList.layoutManager = layoutManager
        binding.aefiParentList.setHasFixedSize(true)

        loadServiceRequests()

        try {
            val referral = FormatterClass().getSharedPref("temp_data", requireContext())
            if (referral != null) {
                val data = Gson().fromJson(referral, DbTempData::class.java)
                binding.apply {
                    tvName.text = data.name
                    tvAge.text = data.age
                    tvGender.text = data.gender
                    tvDob.text = data.dob
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }


    }

    private fun loadServiceRequests() {
        try {
            val data = patientDetailsViewModel.loadServiceRequests()
            val vaccineAdapter =
                ReferralAdapter(
                    data,
                    requireContext()
                )

            binding.aefiParentList.adapter = vaccineAdapter

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                showCancelScreenerQuestionnaireAlertDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun showCancelScreenerQuestionnaireAlertDialog() {
        val patientId = FormatterClass().getSharedPref("patientId", requireContext())
        val intent = Intent(context, PatientDetailActivity::class.java)
        intent.putExtra("patientId", patientId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        requireContext().startActivity(intent)
    }

    private fun onBackPressed() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            showCancelScreenerQuestionnaireAlertDialog()
        }
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ReferralsFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ReferralsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}